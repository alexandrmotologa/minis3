package com.engine.minis3.application.service;

import com.engine.minis3.domain.exception.BadDigestException;
import com.engine.minis3.domain.exception.NoSuchBucketException;
import com.engine.minis3.domain.exception.NoSuchKeyException;
import com.engine.minis3.domain.model.*;
import com.engine.minis3.domain.port.BucketRepositoryPort;
import com.engine.minis3.domain.port.ChunkStorePort;
import com.engine.minis3.domain.port.ObjectMetadataPort;
import com.engine.minis3.infrastructure.config.MiniS3Properties;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class ObjectService {

    private static final Logger log = LoggerFactory.getLogger(ObjectService.class);

    private final BucketRepositoryPort bucketRepository;
    private final ObjectMetadataPort objectMetadata;
    private final ChunkStorePort chunkStore;
    private final int chunkSize;
    private final int zstdLevel;

    public ObjectService(BucketRepositoryPort bucketRepository,
                         ObjectMetadataPort objectMetadata,
                         ChunkStorePort chunkStore,
                         MiniS3Properties properties) {
        this.bucketRepository = bucketRepository;
        this.objectMetadata = objectMetadata;
        this.chunkStore = chunkStore;
        this.chunkSize = properties.getStorage().getChunkSize();
        this.zstdLevel = properties.getStorage().getZstdLevel();
    }

    public S3Object putObject(String bucketName, String key, String contentType,
                             InputStream inputStream, long contentLength, String expectedMd5) {
        if (!bucketRepository.exists(bucketName)) {
            throw new NoSuchBucketException(bucketName);
        }

        List<ObjectChunkRef> chunkRefs = new ArrayList<>();
        MessageDigest overallMd5 = DigestUtils.getMd5Digest();
        long totalBytesRead = 0;
        int chunkOrder = 0;

        try {
            byte[] buffer = new byte[chunkSize];
            int readInCurrentChunk = 0;
            int n;

            // Handle empty file upload
            ByteArrayOutputStream currentChunkStream = new ByteArrayOutputStream();

            while ((n = inputStream.read(buffer)) != -1) {
                currentChunkStream.write(buffer, 0, n);
                overallMd5.update(buffer, 0, n);
                totalBytesRead += n;

                if (currentChunkStream.size() >= chunkSize) {
                    byte[] chunkData = currentChunkStream.toByteArray();
                    ObjectChunkRef ref = processAndStoreChunk(chunkOrder++, totalBytesRead - chunkData.length, chunkData);
                    chunkRefs.add(ref);
                    currentChunkStream.reset();
                }
            }

            if (currentChunkStream.size() > 0 || chunkRefs.isEmpty()) {
                byte[] chunkData = currentChunkStream.toByteArray();
                long offset = totalBytesRead - chunkData.length;
                ObjectChunkRef ref = processAndStoreChunk(chunkOrder++, offset, chunkData);
                chunkRefs.add(ref);
            }

        } catch (IOException e) {
            throw new IllegalStateException("Failed to stream object payload for: " + key, e);
        }

        String computedEtag = Hex.encodeHexString(overallMd5.digest());

        if (expectedMd5 != null && !expectedMd5.isBlank()) {
            validateContentMd5(expectedMd5, computedEtag);
        }

        S3Object s3Object = new S3Object(
                bucketName,
                key,
                totalBytesRead,
                computedEtag,
                contentType,
                Instant.now(),
                chunkRefs
        );

        objectMetadata.save(s3Object);
        log.debug("Stored object s3://{}/{} size={} chunks={}", bucketName, key, totalBytesRead, chunkRefs.size());
        return s3Object;
    }

    private ObjectChunkRef processAndStoreChunk(int order, long offset, byte[] data) {
        String sha256Hex = DigestUtils.sha256Hex(data);
        ChunkHash hash = new ChunkHash(sha256Hex);

        Optional<Chunk> existing = objectMetadata.findChunk(hash);
        if (existing.isPresent()) {
            objectMetadata.incrementChunkRef(hash);
        } else {
            long compressedSize = chunkStore.writeChunk(hash, data, zstdLevel);
            Chunk newChunk = new Chunk(hash, data.length, compressedSize, 1, Instant.now());
            objectMetadata.recordChunk(newChunk);
        }

        return new ObjectChunkRef(order, hash, offset, data.length);
    }

    private void validateContentMd5(String expectedMd5, String computedEtag) {
        String expectedHex = expectedMd5.trim();
        // If provided as Base64, convert to hex
        if (expectedHex.length() == 24 && expectedHex.endsWith("=")) {
            try {
                byte[] decoded = Base64.getDecoder().decode(expectedHex);
                expectedHex = Hex.encodeHexString(decoded);
            } catch (Exception ignored) {}
        }
        if (!expectedHex.equalsIgnoreCase(computedEtag)) {
            throw new BadDigestException("Content-MD5 does not match object digest. Expected: " + expectedHex + ", got: " + computedEtag);
        }
    }

    public S3Object getObject(String bucketName, String key) {
        if (!bucketRepository.exists(bucketName)) {
            throw new NoSuchBucketException(bucketName);
        }
        return objectMetadata.findByBucketAndKey(bucketName, key)
                .orElseThrow(() -> new NoSuchKeyException(key));
    }

    public void streamObject(S3Object object, OutputStream outputStream) throws IOException {
        for (ObjectChunkRef ref : object.getChunks()) {
            byte[] decompressed = chunkStore.readChunk(ref.getHash());
            outputStream.write(decompressed);
        }
        outputStream.flush();
    }

    public byte[] readRange(S3Object object, long start, long end) {
        long objSize = object.getSize();
        if (objSize == 0) {
            return new byte[0];
        }

        long actualStart = Math.max(0, start);
        long actualEnd = Math.min(objSize - 1, end);
        if (actualStart > actualEnd) {
            return new byte[0];
        }

        int targetLength = (int) (actualEnd - actualStart + 1);
        byte[] result = new byte[targetLength];
        int written = 0;

        for (ObjectChunkRef ref : object.getChunks()) {
            long chunkStart = ref.getOffset();
            long chunkEnd = chunkStart + ref.getLength() - 1;

            if (chunkEnd < actualStart || chunkStart > actualEnd) {
                continue;
            }

            byte[] chunkData = chunkStore.readChunk(ref.getHash());
            int readOffsetInChunk = (int) Math.max(0, actualStart - chunkStart);
            int bytesToCopy = (int) (Math.min(chunkEnd, actualEnd) - (chunkStart + readOffsetInChunk) + 1);

            System.arraycopy(chunkData, readOffsetInChunk, result, written, bytesToCopy);
            written += bytesToCopy;

            if (written >= targetLength) {
                break;
            }
        }

        return result;
    }

    public void deleteObject(String bucketName, String key) {
        if (!bucketRepository.exists(bucketName)) {
            throw new NoSuchBucketException(bucketName);
        }
        objectMetadata.delete(bucketName, key);
    }

    public List<S3Object> listObjects(String bucketName, String prefix, String continuationToken, int maxKeys) {
        if (!bucketRepository.exists(bucketName)) {
            throw new NoSuchBucketException(bucketName);
        }
        return objectMetadata.listObjects(bucketName, prefix, continuationToken, maxKeys);
    }
}
