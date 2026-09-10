package com.engine.minis3.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain entity representing an active multipart upload session.
 */
public final class MultipartUpload {

    private final String uploadId;
    private final String bucketName;
    private final String key;
    private final String contentType;
    private final Instant initiatedAt;

    public MultipartUpload(String uploadId, String bucketName, String key, String contentType, Instant initiatedAt) {
        this.uploadId = Objects.requireNonNull(uploadId, "uploadId must not be null");
        this.bucketName = Objects.requireNonNull(bucketName, "bucketName must not be null");
        this.key = Objects.requireNonNull(key, "key must not be null");
        this.contentType = (contentType != null && !contentType.isBlank()) ? contentType : "application/octet-stream";
        this.initiatedAt = Objects.requireNonNull(initiatedAt, "initiatedAt must not be null");
    }

    public String getUploadId() {
        return uploadId;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getKey() {
        return key;
    }

    public String getContentType() {
        return contentType;
    }

    public Instant getInitiatedAt() {
        return initiatedAt;
    }
}
