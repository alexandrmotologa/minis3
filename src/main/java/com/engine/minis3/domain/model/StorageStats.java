package com.engine.minis3.domain.model;

/**
 * Value object summarizing system storage and deduplication metrics.
 */
public final class StorageStats {

    private final long totalBuckets;
    private final long totalObjects;
    private final long totalRawBytes;
    private final long totalCompressedBytes;
    private final long totalUniqueChunks;
    private final double deduplicationRatio;
    private final double spaceSavedPercent;

    public StorageStats(long totalBuckets, long totalObjects, long totalRawBytes,
                        long totalCompressedBytes, long totalUniqueChunks) {
        this.totalBuckets = totalBuckets;
        this.totalObjects = totalObjects;
        this.totalRawBytes = totalRawBytes;
        this.totalCompressedBytes = totalCompressedBytes;
        this.totalUniqueChunks = totalUniqueChunks;

        if (totalCompressedBytes > 0 && totalRawBytes > 0) {
            this.deduplicationRatio = (double) totalRawBytes / (double) totalCompressedBytes;
            this.spaceSavedPercent = Math.max(0.0, (1.0 - ((double) totalCompressedBytes / (double) totalRawBytes)) * 100.0);
        } else {
            this.deduplicationRatio = 1.0;
            this.spaceSavedPercent = 0.0;
        }
    }

    public long getTotalBuckets() {
        return totalBuckets;
    }

    public long getTotalObjects() {
        return totalObjects;
    }

    public long getTotalRawBytes() {
        return totalRawBytes;
    }

    public long getTotalCompressedBytes() {
        return totalCompressedBytes;
    }

    public long getTotalUniqueChunks() {
        return totalUniqueChunks;
    }

    public double getDeduplicationRatio() {
        return deduplicationRatio;
    }

    public double getSpaceSavedPercent() {
        return spaceSavedPercent;
    }
}
