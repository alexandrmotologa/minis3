package com.engine.minis3;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AwsS3ClientCompatibilityIT {

    private static final String TEST_DIR = "target/test-data/s3-sdk-" + UUID.randomUUID().toString().substring(0, 8);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("minis3.storage.root-dir", () -> TEST_DIR);
    }

    @LocalServerPort
    private int port;

    private S3Client s3;

    @BeforeEach
    void setUp() {
        s3 = S3Client.builder()
                .endpointOverride(URI.create("http://localhost:" + port))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("minis3-access-key", "minis3-secret-key")
                ))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .checksumValidationEnabled(false)
                        .build())
                .build();
    }

    @AfterEach
    void tearDown() {
        if (s3 != null) {
            s3.close();
        }
    }

    @Test
    void testOfficialAwsSdkCompatibility() {
        String bucketName = "sdk-bucket-" + UUID.randomUUID().toString().substring(0, 8);
        String objectKey = "sdk-test-file.json";
        String payload = "{\"engine\":\"MiniS3\",\"status\":\"compatible\",\"threads\":\"virtual\"}";

        // 1. Create Bucket
        CreateBucketResponse createBucketResp = s3.createBucket(b -> b.bucket(bucketName));
        assertNotNull(createBucketResp);

        // 2. Head Bucket
        HeadBucketResponse headBucketResp = s3.headBucket(b -> b.bucket(bucketName));
        assertNotNull(headBucketResp);

        // 3. Put Object
        PutObjectResponse putResp = s3.putObject(
                b -> b.bucket(bucketName).key(objectKey).contentType("application/json"),
                RequestBody.fromString(payload, StandardCharsets.UTF_8)
        );
        assertNotNull(putResp);
        assertNotNull(putResp.eTag());

        // 4. Head Object
        HeadObjectResponse headObjResp = s3.headObject(b -> b.bucket(bucketName).key(objectKey));
        assertEquals((long) payload.getBytes(StandardCharsets.UTF_8).length, headObjResp.contentLength());
        assertEquals("application/json", headObjResp.contentType());

        // 5. Get Object
        ResponseBytes<GetObjectResponse> getResp = s3.getObjectAsBytes(b -> b.bucket(bucketName).key(objectKey));
        assertEquals(payload, getResp.asUtf8String());

        // 6. List Objects V2
        ListObjectsV2Response listResp = s3.listObjectsV2(b -> b.bucket(bucketName));
        assertFalse(listResp.contents().isEmpty());
        assertEquals(objectKey, listResp.contents().get(0).key());

        // 7. Delete Object
        s3.deleteObject(b -> b.bucket(bucketName).key(objectKey));

        // 8. Delete Bucket
        s3.deleteBucket(b -> b.bucket(bucketName));
    }
}
