package com.engine.minis3.domain.model;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Domain entity representing an IAM API credential pair and access policy.
 */
public final class ApiCredential {

    public enum Role {
        ADMIN,
        READ_WRITE,
        READ_ONLY;

        public static Role fromString(String text) {
            if (text == null || text.isBlank()) return READ_WRITE;
            for (Role r : values()) {
                if (r.name().equalsIgnoreCase(text.trim())) return r;
            }
            return READ_WRITE;
        }
    }

    private final String accessKey;
    private final String secretKey;
    private final Role role;
    private final String allowedBuckets; // "*" or comma-separated names
    private final Instant createdAt;

    public ApiCredential(String accessKey, String secretKey, Role role, String allowedBuckets, Instant createdAt) {
        this.accessKey = Objects.requireNonNull(accessKey, "accessKey must not be null");
        this.secretKey = Objects.requireNonNull(secretKey, "secretKey must not be null");
        this.role = (role != null) ? role : Role.READ_WRITE;
        this.allowedBuckets = (allowedBuckets != null && !allowedBuckets.isBlank()) ? allowedBuckets.trim() : "*";
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public boolean allowsBucket(String bucketName) {
        if ("*".equals(allowedBuckets)) return true;
        List<String> list = Arrays.stream(allowedBuckets.split(","))
                .map(String::trim)
                .toList();
        return list.contains(bucketName);
    }

    public boolean allowsMethod(String httpMethod) {
        if (role == Role.ADMIN || role == Role.READ_WRITE) return true;
        // READ_ONLY only allows GET, HEAD, OPTIONS
        return "GET".equalsIgnoreCase(httpMethod) ||
               "HEAD".equalsIgnoreCase(httpMethod) ||
               "OPTIONS".equalsIgnoreCase(httpMethod);
    }

    public String getAccessKey() {
        return accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public Role getRole() {
        return role;
    }

    public String getAllowedBuckets() {
        return allowedBuckets;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
