package com.engine.minis3.application.service;

import com.engine.minis3.domain.model.ChunkHash;
import com.engine.minis3.domain.model.ScrubReport;
import com.engine.minis3.domain.port.ChunkStorePort;
import com.engine.minis3.domain.port.ObjectMetadataPort;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class StorageScrubberService {

    private static final Logger log = LoggerFactory.getLogger(StorageScrubberService.class);

    private final ObjectMetadataPort objectMetadata;
    private final ChunkStorePort chunkStore;

    public StorageScrubberService(ObjectMetadataPort objectMetadata, ChunkStorePort chunkStore) {
        this.objectMetadata = objectMetadata;
        this.chunkStore = chunkStore;
    }

    /**
     * Executes a full integrity scrub over all active CAS chunks on disk.
     * Verifies physical presence and checks the cryptographic SHA-256 hash
     * of each decompressed block against the catalog to detect silent bitrot.
     */
    public ScrubReport runScrub() {
        long startTime = System.currentTimeMillis();
        List<ChunkHash> hashes = objectMetadata.listAllChunkHashes();

        long healthy = 0;
        long corrupted = 0;
        long missing = 0;
        List<String> failedHashes = new ArrayList<>();

        for (ChunkHash hash : hashes) {
            if (!chunkStore.exists(hash)) {
                missing++;
                failedHashes.add(hash.getValue() + ": MISSING");
                continue;
            }

            try {
                byte[] data = chunkStore.readChunk(hash);
                String computedHash = DigestUtils.sha256Hex(data);
                if (computedHash.equalsIgnoreCase(hash.getValue())) {
                    healthy++;
                } else {
                    corrupted++;
                    failedHashes.add(hash.getValue() + ": CORRUPTED (computed=" + computedHash + ")");
                }
            } catch (Exception e) {
                corrupted++;
                failedHashes.add(hash.getValue() + ": READ_ERROR (" + e.getMessage() + ")");
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        ScrubReport report = new ScrubReport(
                Instant.now(),
                hashes.size(),
                healthy,
                corrupted,
                missing,
                duration,
                failedHashes
        );

        log.info("Storage scrub completed in {} ms: {} total, {} healthy, {} corrupted, {} missing",
                duration, hashes.size(), healthy, corrupted, missing);
        return report;
    }
}
