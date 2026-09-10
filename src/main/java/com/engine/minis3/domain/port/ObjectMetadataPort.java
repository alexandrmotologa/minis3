package com.engine.minis3.domain.port;

import com.engine.minis3.domain.model.Chunk;
import com.engine.minis3.domain.model.ChunkHash;
import com.engine.minis3.domain.model.ObjectKeyVersion;
import com.engine.minis3.domain.model.S3Object;
import com.engine.minis3.domain.model.StorageStats;

import java.util.List;
import java.util.Optional;

public interface ObjectMetadataPort {
    void save(S3Object object);
    Optional<S3Object> findByBucketAndKey(String bucketName, String key);
    Optional<S3Object> findByBucketAndKeyAndVersion(String bucketName, String key, String versionId);
    void delete(String bucketName, String key);
    void deleteVersion(String bucketName, String key, String versionId);
    void deleteBatch(String bucketName, List<ObjectKeyVersion> targets);
    List<S3Object> listObjects(String bucketName, String prefix, String continuationToken, int maxKeys);
    List<S3Object> listObjectVersions(String bucketName, String prefix, String keyMarker, String versionIdMarker, int maxKeys);
    long countByBucket(String bucketName);
    void recordChunk(Chunk chunk);
    Optional<Chunk> findChunk(ChunkHash hash);
    void incrementChunkRef(ChunkHash hash);
    void decrementChunkRef(ChunkHash hash);
    List<ChunkHash> findOrphanChunks();
    List<ChunkHash> listAllChunkHashes();
    void deleteChunk(ChunkHash hash);
    StorageStats getStorageStats();
}
