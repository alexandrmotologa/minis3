package com.engine.minis3;

import com.engine.minis3.application.event.S3EventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SseEventsAndAdminApiTest {

    private static final Path TEST_DIR = Path.of("target", "test-data", "s3-sse-" + UUID.randomUUID().toString().substring(0, 8));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("minis3.storage.root-dir", () -> TEST_DIR.toString().replace('\\', '/'));
        registry.add("minis3.auth.allow-anonymous", () -> "true");
        registry.add("minis3.auth.access-key", () -> "minis3master");
        registry.add("minis3.auth.secret-key", () -> "minis3mastersecret");
        registry.add("minis3.auth.region", () -> "us-east-1");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private S3EventPublisher eventPublisher;

    @Test
    void testSseSubscriptionAndAdminApiFlow() throws Exception {
        // 1. Subscribe to SSE endpoint
        SseEmitter emitter = eventPublisher.subscribe();
        assertThat(emitter).isNotNull();

        // Verify GET /api/admin/events returns 200 and text/event-stream
        MvcResult sseResult = mockMvc.perform(get("/api/admin/events"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andReturn();
        assertThat(sseResult).isNotNull();

        // 2. Create bucket via Admin API
        String bucket = "telemetry-bucket-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/admin/buckets").param("name", bucket))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(bucket))
                .andExpect(jsonPath("$.versioningStatus").value("OFF"));

        // 3. Update versioning to ENABLED via Admin API
        mockMvc.perform(put("/api/admin/buckets/" + bucket + "/versioning").param("status", "ENABLED"))
                .andExpect(status().isOk());

        // 4. Upload versioned objects
        String key = "telemetry-note.txt";
        mockMvc.perform(put("/" + bucket + "/" + key)
                        .contentType(MediaType.TEXT_PLAIN_VALUE)
                        .content("Version 1 content"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/" + bucket + "/" + key)
                        .contentType(MediaType.TEXT_PLAIN_VALUE)
                        .content("Version 2 updated content"))
                .andExpect(status().isOk());

        // 5. Test GET /api/admin/buckets/{bucket}/versions
        mockMvc.perform(get("/api/admin/buckets/" + bucket + "/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].key").value(key))
                .andExpect(jsonPath("$[0].latest").value(true))
                .andExpect(jsonPath("$[1].key").value(key))
                .andExpect(jsonPath("$[1].latest").value(false));

        // 6. Test Admin Integrity Scrub
        mockMvc.perform(post("/api/admin/scrub"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalChunks").isNumber())
                .andExpect(jsonPath("$.corruptedChunks").value(0))
                .andExpect(jsonPath("$.clean").value(true));

        // 7. Test Admin Presign API
        mockMvc.perform(post("/api/admin/presign")
                        .param("bucket", bucket)
                        .param("key", key)
                        .param("method", "GET")
                        .param("expiresIn", "300"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").isString())
                .andExpect(jsonPath("$.method").value("GET"))
                .andExpect(jsonPath("$.expiresInSeconds").value("300"));

        // 8. Test Vacuum GC
        mockMvc.perform(post("/api/admin/gc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }
}
