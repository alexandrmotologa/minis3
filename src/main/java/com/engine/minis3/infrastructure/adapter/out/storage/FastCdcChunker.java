package com.engine.minis3.infrastructure.adapter.out.storage;

import com.engine.minis3.domain.port.ChunkerPort;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * High-performance Content-Defined Chunking (FastCDC) implementation using Gear hashing
 * with sub-optimal two-phase mask normalization.
 *
 * Solves the boundary-shift problem, enabling resilient deduplication even when bytes
 * are inserted or deleted inside files.
 */
public class FastCdcChunker implements ChunkerPort {

    // Deterministically initialized Gear lookup matrix with 256 well-distributed 64-bit values
    private static final long[] GEAR_MATRIX = new long[256];

    static {
        long seed = 0x853c49e6748fea9bL;
        for (int i = 0; i < 256; i++) {
            seed ^= (seed << 21);
            seed ^= (seed >>> 35);
            seed ^= (seed << 4);
            GEAR_MATRIX[i] = seed;
        }
    }

    private final int minSize;
    private final int targetSize;
    private final int maxSize;
    private final long maskSmall;
    private final long maskLarge;

    public FastCdcChunker(int minSize, int targetSize, int maxSize) {
        this.minSize = Math.max(256, minSize);
        this.targetSize = Math.max(this.minSize * 2, targetSize);
        this.maxSize = Math.max(this.targetSize * 2, maxSize);

        int bits = (int) Math.round(Math.log(this.targetSize) / Math.log(2));
        this.maskSmall = (1L << Math.min(32, bits + 1)) - 1;
        this.maskLarge = (1L << Math.max(1, bits - 1)) - 1;
    }

    public FastCdcChunker() {
        // Defaults: 1MB min, 4MB target, 8MB max
        this(1048576, 4194304, 8388608);
    }

    @Override
    public List<byte[]> chunk(byte[] data) {
        List<byte[]> chunks = new ArrayList<>();
        if (data == null || data.length == 0) {
            chunks.add(new byte[0]);
            return chunks;
        }

        int offset = 0;
        int remaining = data.length;

        while (remaining > 0) {
            if (remaining <= minSize) {
                chunks.add(Arrays.copyOfRange(data, offset, offset + remaining));
                break;
            }

            int chunkLen = findCutPoint(data, offset, remaining);
            chunks.add(Arrays.copyOfRange(data, offset, offset + chunkLen));
            offset += chunkLen;
            remaining -= chunkLen;
        }

        return chunks;
    }

    private int findCutPoint(byte[] data, int offset, int remaining) {
        int windowMax = Math.min(remaining, maxSize);
        if (windowMax <= minSize) {
            return windowMax;
        }

        long fingerprint = 0;
        int i = minSize;

        // Phase 1: tighter mask before reaching targetSize
        int targetLimit = Math.min(windowMax, targetSize);
        for (; i < targetLimit; i++) {
            byte b = data[offset + i];
            fingerprint = (fingerprint << 1) + GEAR_MATRIX[b & 0xFF];
            if ((fingerprint & maskSmall) == 0) {
                return i + 1;
            }
        }

        // Phase 2: relaxed mask between targetSize and windowMax
        for (; i < windowMax; i++) {
            byte b = data[offset + i];
            fingerprint = (fingerprint << 1) + GEAR_MATRIX[b & 0xFF];
            if ((fingerprint & maskLarge) == 0) {
                return i + 1;
            }
        }

        return windowMax;
    }
}
