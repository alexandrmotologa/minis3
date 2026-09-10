package com.engine.minis3.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a physical content-addressed chunk stored in the CAS repository.
 */
public final class Chunk {

    private final ChunkHash hash;
    private final long rawSize;
    private final long compressedSize;
    private final long refCount;
    private final Instant createdAt;

    public Chunk(ChunkHash hash, long rawSize, long compressedSize, long refCount, Instant createdAt) {
        this.hash = Objects.requireNonNull(hash, "hash must not be null");
        this.rawSize = rawSize;
        this.compressedSize = compressedSize;
        this.refCount = refCount;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public ChunkHash getHash() {
        return hash;
    }

    public long getRawSize() {
        return rawSize;
    }

    public long getCompressedSize() {
        return compressedSize;
    }

    public long getRefCount() {
        return refCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Chunk withIncrementedRef() {
        return new Chunk(hash, rawSize, compressedSize, refCount + 1, createdAt);
    }

    public Chunk withDecrementedRef() {
        return new Chunk(hash, rawSize, compressedSize, Math.max(0, refCount - 1), createdAt);
    }
}
