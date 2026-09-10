package com.engine.minis3.domain.model;

import com.engine.minis3.domain.exception.InvalidBucketNameException;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Domain entity representing an S3 Bucket.
 */
public final class Bucket {

    private static final Pattern BUCKET_NAME_PATTERN =
            Pattern.compile("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$");

    private final String name;
    private final Instant createdAt;

    public Bucket(String name, Instant createdAt) {
        validateName(name);
        this.name = name;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static void validateName(String name) {
        if (name == null || name.length() < 3 || name.length() > 63) {
            throw new InvalidBucketNameException("Bucket name must be between 3 and 63 characters long");
        }
        if (!BUCKET_NAME_PATTERN.matcher(name).matches() || name.contains("..") || name.contains(".-") || name.contains("-.")) {
            throw new InvalidBucketNameException("Bucket name does not conform to S3 naming conventions: " + name);
        }
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Bucket bucket = (Bucket) o;
        return Objects.equals(name, bucket.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
