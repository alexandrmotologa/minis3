package com.engine.minis3.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain entity representing an uploaded part of a multipart session.
 */
public final class MultipartPart {

    private final String uploadId;
    private final int partNumber;
    private final String etag;
    private final long size;
    private final ChunkHash chunkHash;
    private final Instant uploadedAt;

    public MultipartPart(String uploadId, int partNumber, String etag, long size, ChunkHash chunkHash, Instant uploadedAt) {
        this.uploadId = Objects.requireNonNull(uploadId, "uploadId must not be null");
        if (partNumber < 1 || partNumber > 10000) {
            throw new IllegalArgumentException("Part number must be between 1 and 10000: " + partNumber);
        }
        this.partNumber = partNumber;
        this.etag = Objects.requireNonNull(etag, "etag must not be null");
        if (size < 0) {
            throw new IllegalArgumentException("size cannot be negative: " + size);
        }
        this.size = size;
        this.chunkHash = Objects.requireNonNull(chunkHash, "chunkHash must not be null");
        this.uploadedAt = Objects.requireNonNull(uploadedAt, "uploadedAt must not be null");
    }

    public String getUploadId() {
        return uploadId;
    }

    public int getPartNumber() {
        return partNumber;
    }

    public String getEtag() {
        return etag;
    }

    public long getSize() {
        return size;
    }

    public ChunkHash getChunkHash() {
        return chunkHash;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
