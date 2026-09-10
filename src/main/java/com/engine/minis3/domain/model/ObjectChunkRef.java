package com.engine.minis3.domain.model;

import java.util.Objects;

/**
 * References an ordered slice of an S3Object backed by a content-addressed chunk.
 */
public final class ObjectChunkRef {

    private final int order;
    private final ChunkHash hash;
    private final long offset;
    private final long length;

    public ObjectChunkRef(int order, ChunkHash hash, long offset, long length) {
        if (order < 0) {
            throw new IllegalArgumentException("Chunk order cannot be negative: " + order);
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Chunk offset cannot be negative: " + offset);
        }
        if (length < 0) {
            throw new IllegalArgumentException("Chunk length cannot be negative: " + length);
        }
        this.order = order;
        this.hash = Objects.requireNonNull(hash, "hash must not be null");
        this.offset = offset;
        this.length = length;
    }

    public int getOrder() {
        return order;
    }

    public ChunkHash getHash() {
        return hash;
    }

    public long getOffset() {
        return offset;
    }

    public long getLength() {
        return length;
    }
}
