package com.engine.minis3.domain.port;

import com.engine.minis3.domain.model.Chunk;
import com.engine.minis3.domain.model.ChunkHash;
import com.engine.minis3.domain.model.S3Object;
import com.engine.minis3.domain.model.StorageStats;

import java.util.List;
import java.util.Optional;

public interface ObjectMetadataPort {
    void save(S3Object object);
    Optional<S3Object> findByBucketAndKey(String bucketName, String key);
    void delete(String bucketName, String key);
    List<S3Object> listObjects(String bucketName, String prefix, String continuationToken, int maxKeys);
    long countByBucket(String bucketName);
    void recordChunk(Chunk chunk);
    Optional<Chunk> findChunk(ChunkHash hash);
    void incrementChunkRef(ChunkHash hash);
    void decrementChunkRef(ChunkHash hash);
    List<ChunkHash> findOrphanChunks();
    void deleteChunk(ChunkHash hash);
    StorageStats getStorageStats();
}
