package com.engine.minis3;

import com.engine.minis3.domain.model.ChunkHash;
import com.engine.minis3.infrastructure.adapter.out.storage.ContentAddressableChunkStore;
import com.engine.minis3.infrastructure.config.MiniS3Properties;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ContentAddressableChunkStoreTest {

    @TempDir
    Path tempDir;

    private ContentAddressableChunkStore chunkStore;

    @BeforeEach
    void setUp() {
        MiniS3Properties props = new MiniS3Properties();
        props.getStorage().setRootDir(tempDir.toString());
        chunkStore = new ContentAddressableChunkStore(props);
        chunkStore.init();
    }

    @Test
    void testWriteAndReadChunk() {
        byte[] payload = "Hello World! This is a test chunk stored in CAS with Zstandard.".getBytes(StandardCharsets.UTF_8);
        String hashHex = DigestUtils.sha256Hex(payload);
        ChunkHash hash = new ChunkHash(hashHex);

        assertFalse(chunkStore.exists(hash));

        long compressed = chunkStore.writeChunk(hash, payload, 3);
        assertTrue(compressed > 0);
        assertTrue(chunkStore.exists(hash));

        byte[] retrieved = chunkStore.readChunk(hash);
        assertArrayEquals(payload, retrieved);
    }

    @Test
    void testZstdCompressionEfficiency() {
        // Highly compressible repetitive text
        byte[] repetitive = "MINIS3_DEDUP_TEST_REPEAT_REPEAT_REPEAT_123456789\n".repeat(2000).getBytes(StandardCharsets.UTF_8);
        String hashHex = DigestUtils.sha256Hex(repetitive);
        ChunkHash hash = new ChunkHash(hashHex);

        long compressedSize = chunkStore.writeChunk(hash, repetitive, 3);
        assertTrue(compressedSize < repetitive.length / 5, "Repetitive data should be compressed significantly");

        byte[] decompressed = chunkStore.readChunk(hash);
        assertArrayEquals(repetitive, decompressed);
    }
}
