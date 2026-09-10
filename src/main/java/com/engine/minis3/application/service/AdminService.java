package com.engine.minis3.application.service;

import com.engine.minis3.domain.model.ChunkHash;
import com.engine.minis3.domain.model.StorageStats;
import com.engine.minis3.domain.port.ChunkStorePort;
import com.engine.minis3.domain.port.ObjectMetadataPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final ObjectMetadataPort objectMetadata;
    private final ChunkStorePort chunkStore;

    public AdminService(ObjectMetadataPort objectMetadata, ChunkStorePort chunkStore) {
        this.objectMetadata = objectMetadata;
        this.chunkStore = chunkStore;
    }

    public StorageStats getStats() {
        return objectMetadata.getStorageStats();
    }

    public long runGarbageCollection() {
        List<ChunkHash> orphans = objectMetadata.findOrphanChunks();
        long deletedCount = 0;
        for (ChunkHash hash : orphans) {
            try {
                chunkStore.deleteChunk(hash);
                objectMetadata.deleteChunk(hash);
                deletedCount++;
            } catch (Exception e) {
                log.warn("Failed to purge orphan chunk: {}", hash, e);
            }
        }
        log.info("Garbage collection reclaimed {} unreferenced chunks", deletedCount);
        return deletedCount;
    }
}
