package com.engine.minis3.domain.port;

import java.util.List;

/**
 * Port for content-addressable partitioning of raw byte streams into discrete chunks.
 */
public interface ChunkerPort {

    /**
     * Splits byte data into contiguous byte chunks according to the configured chunking strategy.
     *
     * @param data unpartitioned byte payload
     * @return ordered list of chunk byte arrays
     */
    List<byte[]> chunk(byte[] data);
}
