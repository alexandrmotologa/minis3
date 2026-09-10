package com.engine.minis3;

import com.engine.minis3.application.service.StorageScrubberService;
import com.engine.minis3.domain.model.ScrubReport;
import com.engine.minis3.infrastructure.adapter.out.storage.ContentAddressableChunkStore;
import com.engine.minis3.infrastructure.adapter.out.storage.FastCdcChunker;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FastCdcAndScrubberTest {

    private static final Path TEST_DIR = Path.of("target", "test-data", "s3-cdc-" + UUID.randomUUID().toString().substring(0, 8));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("minis3.storage.root-dir", () -> TEST_DIR.toString().replace('\\', '/'));
        registry.add("minis3.auth.allow-anonymous", () -> "true");
        registry.add("minis3.storage.chunking-strategy", () -> "FAST_CDC");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StorageScrubberService scrubberService;

    @Test
    void testFastCdcBoundaryShiftResistance() {
        FastCdcChunker chunker = new FastCdcChunker(512, 2048, 8192);

        // Generate realistic data (512 KB)
        byte[] original = new byte[524288];
        new Random(42).nextBytes(original);

        List<byte[]> chunksOriginal = chunker.chunk(original);
        assertTrue(chunksOriginal.size() > 10, "Expected multiple chunks for 512 KB");

        // Prepend 17 bytes to test boundary-shift resistance
        byte[] shifted = new byte[original.length + 17];
        Arrays.fill(shifted, 0, 17, (byte) 0xAA);
        System.arraycopy(original, 0, shifted, 17, original.length);

        List<byte[]> chunksShifted = chunker.chunk(shifted);

        // Compute SHA-256 hashes for both sets
        List<String> originalHashes = chunksOriginal.stream().map(DigestUtils::sha256Hex).toList();
        List<String> shiftedHashes = chunksShifted.stream().map(DigestUtils::sha256Hex).toList();

        // At least some subsequent chunks must match identical SHA-256 hashes despite 17-byte prefix shift!
        long matched = shiftedHashes.stream().filter(originalHashes::contains).count();
        assertTrue(matched >= 1, "FastCDC should preserve identical chunks despite shift, but matched: " + matched);
    }

    @Test
    void testEntropyCalculation() {
        byte[] repetitive = new byte[4096];
        Arrays.fill(repetitive, (byte) 'A');
        double lowEntropy = ContentAddressableChunkStore.calculateEntropy(repetitive);
        assertEquals(0.0, lowEntropy, 0.01);

        byte[] randomBytes = new byte[4096];
        new Random(42).nextBytes(randomBytes);
        double highEntropy = ContentAddressableChunkStore.calculateEntropy(randomBytes);
        assertTrue(highEntropy > 7.5, "Random bytes should exhibit high entropy, got: " + highEntropy);
    }

    @Test
    void testStorageScrubberEndpoint() throws Exception {
        String bucket = "scrub-test-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(put("/" + bucket)).andExpect(status().isOk());

        // Put an object
        mockMvc.perform(put("/" + bucket + "/data.bin")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                        .content("Integrity verification payload content"))
                .andExpect(status().isOk());

        // Run integrity scrub via Admin API
        mockMvc.perform(post("/api/admin/scrub"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthyChunks").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.corruptedChunks").value(0))
                .andExpect(jsonPath("$.missingChunks").value(0));

        ScrubReport directReport = scrubberService.runScrub();
        assertTrue(directReport.isClean());
        assertTrue(directReport.healthyChunks() >= 1);
    }
}
