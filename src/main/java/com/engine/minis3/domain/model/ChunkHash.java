package com.engine.minis3.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value object representing a 64-character lowercase hexadecimal SHA-256 hash.
 */
public final class ChunkHash {

    private static final Pattern HEX_PATTERN = Pattern.compile("^[a-f0-9]{64}$");

    private final String value;

    public ChunkHash(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Hash value must not be null");
        }
        String normalized = value.trim().toLowerCase();
        if (!HEX_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid SHA-256 hex string: " + value);
        }
        this.value = normalized;
    }

    public String getValue() {
        return value;
    }

    /**
     * Generates a 2-level directory shard path, e.g. "ab/cd" for "abcd1234...".
     */
    public String getShardDirectory() {
        return value.substring(0, 2) + "/" + value.substring(2, 4);
    }

    /**
     * Generates the sharded relative file path, e.g. "ab/cd/<hash>.zstd".
     */
    public String getShardPath() {
        return getShardDirectory() + "/" + value + ".zstd";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChunkHash chunkHash = (ChunkHash) o;
        return Objects.equals(value, chunkHash.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
