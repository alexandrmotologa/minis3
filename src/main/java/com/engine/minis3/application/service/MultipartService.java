package com.engine.minis3.application.service;

import com.engine.minis3.application.dto.CompleteMultipartUploadRequest;
import com.engine.minis3.domain.exception.BadDigestException;
import com.engine.minis3.domain.exception.InvalidPartException;
import com.engine.minis3.domain.exception.NoSuchBucketException;
import com.engine.minis3.domain.exception.NoSuchUploadException;
import com.engine.minis3.domain.model.*;
import com.engine.minis3.domain.port.BucketRepositoryPort;
import com.engine.minis3.domain.port.ChunkStorePort;
import com.engine.minis3.domain.port.MultipartMetadataPort;
import com.engine.minis3.domain.port.ObjectMetadataPort;
import com.engine.minis3.infrastructure.config.MiniS3Properties;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class MultipartService {

    private final BucketRepositoryPort bucketRepository;
    private final ObjectMetadataPort objectMetadata;
    private final MultipartMetadataPort multipartMetadata;
    private final ChunkStorePort chunkStore;
    private final int zstdLevel;

    public MultipartService(BucketRepositoryPort bucketRepository,
                            ObjectMetadataPort objectMetadata,
                            MultipartMetadataPort multipartMetadata,
                            ChunkStorePort chunkStore,
                            MiniS3Properties properties) {
        this.bucketRepository = bucketRepository;
        this.objectMetadata = objectMetadata;
        this.multipartMetadata = multipartMetadata;
        this.chunkStore = chunkStore;
        this.zstdLevel = properties.getStorage().getZstdLevel();
    }

    public MultipartUpload initiateMultipartUpload(String bucketName, String key, String contentType) {
        if (!bucketRepository.exists(bucketName)) {
            throw new NoSuchBucketException(bucketName);
        }
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        MultipartUpload upload = new MultipartUpload(uploadId, bucketName, key, contentType, Instant.now());
        multipartMetadata.saveUpload(upload);
        return upload;
    }

    public MultipartPart uploadPart(String uploadId, int partNumber, InputStream inputStream,
                                   long contentLength, String expectedMd5) {
        MultipartUpload upload = multipartMetadata.findUpload(uploadId)
                .orElseThrow(() -> new NoSuchUploadException(uploadId));

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        MessageDigest md5Digest = DigestUtils.getMd5Digest();

        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = inputStream.read(buf)) != -1) {
                buffer.write(buf, 0, n);
                md5Digest.update(buf, 0, n);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read part data", e);
        }

        byte[] partData = buffer.toByteArray();
        String computedEtag = Hex.encodeHexString(md5Digest.digest());

        if (expectedMd5 != null && !expectedMd5.isBlank()) {
            String cleanExpected = expectedMd5.trim();
            if (!cleanExpected.equalsIgnoreCase(computedEtag)) {
                throw new BadDigestException("Part MD5 mismatch. Expected: " + cleanExpected + ", got: " + computedEtag);
            }
        }

        String sha256 = DigestUtils.sha256Hex(partData);
        ChunkHash hash = new ChunkHash(sha256);

        Optional<Chunk> existing = objectMetadata.findChunk(hash);
        if (existing.isPresent()) {
            objectMetadata.incrementChunkRef(hash);
        } else {
            long compressedSize = chunkStore.writeChunk(hash, partData, zstdLevel);
            Chunk newChunk = new Chunk(hash, partData.length, compressedSize, 1, Instant.now());
            objectMetadata.recordChunk(newChunk);
        }

        MultipartPart part = new MultipartPart(
                uploadId,
                partNumber,
                computedEtag,
                partData.length,
                hash,
                Instant.now()
        );

        multipartMetadata.savePart(part);
        return part;
    }

    public S3Object completeMultipartUpload(String uploadId, List<CompleteMultipartUploadRequest.PartRequestDto> clientParts) {
        MultipartUpload upload = multipartMetadata.findUpload(uploadId)
                .orElseThrow(() -> new NoSuchUploadException(uploadId));

        List<MultipartPart> savedParts = multipartMetadata.listParts(uploadId);
        Map<Integer, MultipartPart> partsByNumber = new HashMap<>();
        for (MultipartPart sp : savedParts) {
            partsByNumber.put(sp.getPartNumber(), sp);
        }

        List<ObjectChunkRef> chunkRefs = new ArrayList<>();
        ByteArrayOutputStream concatenatedMd5Bytes = new ByteArrayOutputStream();
        long totalSize = 0;
        int order = 0;

        for (CompleteMultipartUploadRequest.PartRequestDto clientPart : clientParts) {
            MultipartPart stored = partsByNumber.get(clientPart.getPartNumber());
            if (stored == null) {
                throw new InvalidPartException("Part " + clientPart.getPartNumber() + " not found for upload " + uploadId);
            }

            try {
                byte[] partMd5Bytes = Hex.decodeHex(stored.getEtag().replace("\"", ""));
                concatenatedMd5Bytes.write(partMd5Bytes);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to decode stored part ETag", e);
            }

            chunkRefs.add(new ObjectChunkRef(order++, stored.getChunkHash(), totalSize, stored.getSize()));
            totalSize += stored.getSize();
        }

        // Compute multipart ETag: MD5 of concatenated MD5s + '-' + partCount
        String compositeMd5 = Hex.encodeHexString(DigestUtils.md5(concatenatedMd5Bytes.toByteArray()));
        String multipartEtag = compositeMd5 + "-" + clientParts.size();

        S3Object s3Object = new S3Object(
                upload.getBucketName(),
                upload.getKey(),
                totalSize,
                multipartEtag,
                upload.getContentType(),
                Instant.now(),
                chunkRefs
        );

        objectMetadata.save(s3Object);
        multipartMetadata.deleteUpload(uploadId);
        return s3Object;
    }

    public void abortMultipartUpload(String uploadId) {
        MultipartUpload upload = multipartMetadata.findUpload(uploadId)
                .orElseThrow(() -> new NoSuchUploadException(uploadId));

        List<MultipartPart> parts = multipartMetadata.listParts(uploadId);
        for (MultipartPart p : parts) {
            objectMetadata.decrementChunkRef(p.getChunkHash());
        }

        multipartMetadata.deleteUpload(uploadId);
    }

    public List<MultipartPart> listParts(String uploadId) {
        if (multipartMetadata.findUpload(uploadId).isEmpty()) {
            throw new NoSuchUploadException(uploadId);
        }
        return multipartMetadata.listParts(uploadId);
    }
}
