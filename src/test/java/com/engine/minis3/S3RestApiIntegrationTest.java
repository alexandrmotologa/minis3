package com.engine.minis3;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class S3RestApiIntegrationTest {

    private static final String TEST_DIR = "target/test-data/s3-mockmvc-" + UUID.randomUUID().toString().substring(0, 8);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("minis3.storage.root-dir", () -> TEST_DIR);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testFullS3LifecycleWithDeduplication() throws Exception {
        String bucket = "test-bucket-" + UUID.randomUUID().toString().substring(0, 8);

        // 1. Create Bucket: PUT /{bucket}
        mockMvc.perform(put("/" + bucket))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.LOCATION, "/" + bucket));

        // 2. Head Bucket: HEAD /{bucket}
        mockMvc.perform(head("/" + bucket))
                .andExpect(status().isOk());

        // 3. List Buckets: GET /
        mockMvc.perform(get("/").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString(bucket)));

        // 4. Put Object: PUT /{bucket}/{key}
        String content = "MiniS3 content-addressable storage test payload!";
        mockMvc.perform(put("/" + bucket + "/payload.txt")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(content))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG));

        // 5. Head Object: HEAD /{bucket}/{key}
        mockMvc.perform(head("/" + bucket + "/payload.txt"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(content.length())))
                .andExpect(header().exists(HttpHeaders.ETAG));

        // 6. Get Object: GET /{bucket}/{key}
        mockMvc.perform(get("/" + bucket + "/payload.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(content));

        // 7. Byte-Range Request: GET /{bucket}/{key} with Range: bytes=0-5
        mockMvc.perform(get("/" + bucket + "/payload.txt")
                        .header(HttpHeaders.RANGE, "bytes=0-5"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-5/" + content.length()))
                .andExpect(content().string("MiniS3"));

        // 8. Deduplication Test: Upload same payload with a different key in same or different bucket
        mockMvc.perform(put("/" + bucket + "/payload-copy.txt")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(content))
                .andExpect(status().isOk());

        // 9. Check Telemetry: /api/admin/stats
        mockMvc.perform(get("/api/admin/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalObjects").value(2));

        // 10. List Objects: GET /{bucket}?list-type=2
        mockMvc.perform(get("/" + bucket).param("list-type", "2"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString("payload.txt")))
                .andExpect(content().string(containsString("payload-copy.txt")));

        // 11. Delete Object: DELETE /{bucket}/{key}
        mockMvc.perform(delete("/" + bucket + "/payload.txt"))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/" + bucket + "/payload-copy.txt"))
                .andExpect(status().isNoContent());

        // 12. Run GC: POST /api/admin/gc
        mockMvc.perform(post("/api/admin/gc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // 13. Delete Bucket: DELETE /{bucket}
        mockMvc.perform(delete("/" + bucket))
                .andExpect(status().isNoContent());
    }
}
