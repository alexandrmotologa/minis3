package com.engine.minis3.domain.port;

import com.engine.minis3.domain.model.ChunkHash;

public interface ChunkStorePort {
    long writeChunk(ChunkHash hash, byte[] uncompressedData, int zstdLevel);
    byte[] readChunk(ChunkHash hash);
    void deleteChunk(ChunkHash hash);
    boolean exists(ChunkHash hash);
}
