package com.engine.minis3;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class S3VersioningAndMultiDeleteTest {

    private static final Path TEST_DIR = Path.of("target", "test-data", "s3-ver-" + UUID.randomUUID().toString().substring(0, 8));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("minis3.storage.root-dir", () -> TEST_DIR.toString().replace('\\', '/'));
        registry.add("minis3.auth.allow-anonymous", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testVersioningLifecycleAndMultiDelete() throws Exception {
        String bucket = "versioned-bucket-" + UUID.randomUUID().toString().substring(0, 8);
        String key = "doc.txt";

        // 1. Create bucket
        mockMvc.perform(put("/" + bucket))
                .andExpect(status().isOk());

        // 2. Enable versioning
        String versioningXml = """
                <VersioningConfiguration xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
                    <Status>Enabled</Status>
                </VersioningConfiguration>
                """;
        mockMvc.perform(put("/" + bucket)
                        .param("versioning", "")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(versioningXml))
                .andExpect(status().isOk());

        // 3. Verify versioning status
        mockMvc.perform(get("/" + bucket).param("versioning", ""))
                .andExpect(status().isOk())
                .andExpect(xpath("/VersioningConfiguration/Status").string("Enabled"));

        // 4. Upload Version 1
        String v1Result = mockMvc.perform(put("/" + bucket + "/" + key)
                        .contentType(MediaType.TEXT_PLAIN_VALUE)
                        .content("Version 1 content"))
                .andExpect(status().isOk())
                .andExpect(header().exists("x-amz-version-id"))
                .andReturn().getResponse().getHeader("x-amz-version-id");

        // 5. Upload Version 2
        String v2Result = mockMvc.perform(put("/" + bucket + "/" + key)
                        .contentType(MediaType.TEXT_PLAIN_VALUE)
                        .content("Version 2 content updated"))
                .andExpect(status().isOk())
                .andExpect(header().exists("x-amz-version-id"))
                .andReturn().getResponse().getHeader("x-amz-version-id");

        // 6. Get latest version (should be v2)
        mockMvc.perform(get("/" + bucket + "/" + key))
                .andExpect(status().isOk())
                .andExpect(header().string("x-amz-version-id", v2Result))
                .andExpect(content().string("Version 2 content updated"));

        // 7. Get older version by versionId
        mockMvc.perform(get("/" + bucket + "/" + key).param("versionId", v1Result))
                .andExpect(status().isOk())
                .andExpect(header().string("x-amz-version-id", v1Result))
                .andExpect(content().string("Version 1 content"));

        // 8. List versions
        mockMvc.perform(get("/" + bucket).param("versions", ""))
                .andExpect(status().isOk())
                .andExpect(xpath("/ListVersionsResult/Name").string(bucket))
                .andExpect(xpath("/ListVersionsResult/Version").nodeCount(2));

        // 9. Delete without versionId -> creates Delete Marker
        mockMvc.perform(delete("/" + bucket + "/" + key))
                .andExpect(status().isNoContent());

        // 10. Normal GET now returns 404
        mockMvc.perform(get("/" + bucket + "/" + key))
                .andExpect(status().isNotFound());

        // 11. But v1 is still retrievable by explicit versionId
        mockMvc.perform(get("/" + bucket + "/" + key).param("versionId", v1Result))
                .andExpect(status().isOk())
                .andExpect(content().string("Version 1 content"));

        // 12. Multi-Object Delete
        // Create another file
        mockMvc.perform(put("/" + bucket + "/other.txt").content("hello"))
                .andExpect(status().isOk());

        String multiDeleteXml = """
                <Delete xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
                    <Object><Key>other.txt</Key></Object>
                </Delete>
                """;

        mockMvc.perform(post("/" + bucket)
                        .param("delete", "")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(multiDeleteXml))
                .andExpect(status().isOk())
                .andExpect(xpath("/DeleteResult/Deleted/Key").string("other.txt"));
    }
}
