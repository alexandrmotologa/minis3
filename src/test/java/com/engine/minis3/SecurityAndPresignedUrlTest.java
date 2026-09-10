package com.engine.minis3;

import com.engine.minis3.domain.auth.SigV4Signer;
import com.engine.minis3.domain.model.ApiCredential;
import com.engine.minis3.domain.port.CredentialRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityAndPresignedUrlTest {

    private static final Path TEST_DIR = Path.of("target", "test-data", "s3-sec-" + UUID.randomUUID().toString().substring(0, 8));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("minis3.storage.root-dir", () -> TEST_DIR.toString().replace('\\', '/'));
        registry.add("minis3.auth.allow-anonymous", () -> "false");
        registry.add("minis3.auth.require-auth", () -> "true");
        registry.add("minis3.auth.access-key", () -> "minis3master");
        registry.add("minis3.auth.secret-key", () -> "minis3mastersecret");
        registry.add("minis3.auth.region", () -> "us-east-1");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CredentialRepositoryPort credentialRepository;

    @Test
    void testPresignedUrlAndIamAuthorization() throws Exception {
        String bucket = "secure-bucket-" + UUID.randomUUID().toString().substring(0, 8);
        String key = "secret-doc.txt";

        // 1. Create bucket via Admin API (which bypasses SigV4)
        mockMvc.perform(post("/api/admin/buckets").param("name", bucket))
                .andExpect(status().isOk());

        // Upload initial object using a presigned PUT URL
        String presignedPutInitial = SigV4Signer.generatePresignedUrl(
                "http://localhost",
                "PUT",
                bucket,
                key,
                "minis3master",
                "minis3mastersecret",
                "us-east-1",
                300
        );
        URI initialPutUri = URI.create(presignedPutInitial);
        mockMvc.perform(put(initialPutUri.getRawPath() + "?" + initialPutUri.getRawQuery())
                        .contentType(MediaType.TEXT_PLAIN_VALUE)
                        .content("Top Secret CAS Payload"))
                .andExpect(status().isOk());

        // 2. Generate Presigned URL via SigV4Signer
        String presignedUrl = SigV4Signer.generatePresignedUrl(
                "http://localhost",
                "GET",
                bucket,
                key,
                "minis3master",
                "minis3mastersecret",
                "us-east-1",
                300
        );

        URI uri = URI.create(presignedUrl);
        String relativePathAndQuery = uri.getRawPath() + "?" + uri.getRawQuery();

        // 3. Fetch object using the presigned URL
        mockMvc.perform(get(relativePathAndQuery))
                .andExpect(status().isOk())
                .andExpect(content().string("Top Secret CAS Payload"));

        // 4. Test Multi-Credential IAM: Create a READ_ONLY credential scoped to this bucket
        String customAccessKey = "AKIA_READONLY_" + UUID.randomUUID().toString().substring(0, 4);
        String customSecretKey = "secret_key_" + UUID.randomUUID();
        ApiCredential readOnlyCred = new ApiCredential(
                customAccessKey,
                customSecretKey,
                ApiCredential.Role.READ_ONLY,
                bucket,
                Instant.now()
        );
        credentialRepository.saveCredential(readOnlyCred);

        // 5. Test GET with READ_ONLY credentials -> Allowed (200 OK)
        String readOnlyPresignedGet = SigV4Signer.generatePresignedUrl(
                "http://localhost",
                "GET",
                bucket,
                key,
                customAccessKey,
                customSecretKey,
                "us-east-1",
                300
        );
        URI getUri = URI.create(readOnlyPresignedGet);
        mockMvc.perform(get(getUri.getRawPath() + "?" + getUri.getRawQuery()))
                .andExpect(status().isOk())
                .andExpect(content().string("Top Secret CAS Payload"));

        // 6. Test PUT with READ_ONLY credentials -> Denied (403 Forbidden)
        String readOnlyPresignedPut = SigV4Signer.generatePresignedUrl(
                "http://localhost",
                "PUT",
                bucket,
                "forbidden-write.txt",
                customAccessKey,
                customSecretKey,
                "us-east-1",
                300
        );
        URI putUri = URI.create(readOnlyPresignedPut);
        mockMvc.perform(put(putUri.getRawPath() + "?" + putUri.getRawQuery()).content("denied"))
                .andExpect(status().isForbidden());
    }

    private String createAuthHeader(String method, String uri, String accessKey, String secretKey) {
        String presigned = SigV4Signer.generatePresignedUrl("http://localhost", method, uri.substring(1).split("/")[0], uri.substring(1).split("/")[1], accessKey, secretKey, "us-east-1", 60);
        // Returns dummy for header or uses query string; let's use presigned queries directly in test
        return "";
    }
}
