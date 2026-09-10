package com.engine.minis3.domain.model;

/**
 * Storage chunking algorithm strategy.
 */
public enum ChunkingStrategy {
    FIXED,
    FAST_CDC;

    public static ChunkingStrategy fromString(String text) {
        if (text == null || text.isBlank()) {
            return FAST_CDC;
        }
        for (ChunkingStrategy s : values()) {
            if (s.name().equalsIgnoreCase(text.replace('-', '_'))) {
                return s;
            }
        }
        return FAST_CDC;
    }
}
