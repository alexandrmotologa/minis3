package com.engine.minis3.domain.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Domain entity representing an object stored in a bucket.
 */
public final class S3Object {

    private final String bucketName;
    private final String key;
    private final long size;
    private final String etag;
    private final String contentType;
    private final Instant createdAt;
    private final List<ObjectChunkRef> chunks;

    public S3Object(String bucketName, String key, long size, String etag,
                    String contentType, Instant createdAt, List<ObjectChunkRef> chunks) {
        this.bucketName = Objects.requireNonNull(bucketName, "bucketName must not be null");
        this.key = Objects.requireNonNull(key, "key must not be null");
        if (size < 0) {
            throw new IllegalArgumentException("size cannot be negative: " + size);
        }
        this.size = size;
        this.etag = Objects.requireNonNull(etag, "etag must not be null");
        this.contentType = (contentType != null && !contentType.isBlank()) ? contentType : "application/octet-stream";
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.chunks = (chunks != null) ? List.copyOf(chunks) : Collections.emptyList();
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getKey() {
        return key;
    }

    public long getSize() {
        return size;
    }

    public String getEtag() {
        return etag;
    }

    public String getContentType() {
        return contentType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<ObjectChunkRef> getChunks() {
        return chunks;
    }
}
