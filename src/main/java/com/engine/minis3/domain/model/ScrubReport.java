package com.engine.minis3.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Results of a storage integrity scrub audit.
 */
public record ScrubReport(
        Instant scannedAt,
        long totalChunks,
        long healthyChunks,
        long corruptedChunks,
        long missingChunks,
        long durationMs,
        List<String> corruptedHashes
) {
    public boolean isClean() {
        return corruptedChunks == 0 && missingChunks == 0;
    }
}
