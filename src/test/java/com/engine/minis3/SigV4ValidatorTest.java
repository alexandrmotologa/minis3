package com.engine.minis3;

import com.engine.minis3.domain.auth.CanonicalRequest;
import com.engine.minis3.domain.auth.SigV4Credentials;
import com.engine.minis3.domain.auth.SigV4Validator;
import com.engine.minis3.domain.exception.SignatureDoesNotMatchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SigV4ValidatorTest {

    private SigV4Validator validator;
    private SigV4Credentials credentials;

    @BeforeEach
    void setUp() {
        credentials = new SigV4Credentials("minis3-access-key", "minis3-secret-key", "us-east-1");
        validator = new SigV4Validator(credentials);
    }

    @Test
    void testDerivedSigningKey() {
        byte[] key = SigV4Validator.deriveSigningKey("minis3-secret-key", "20260910", "us-east-1", "s3");
        assertNotNull(key);
        assertTrue(key.length > 0);
    }

    @Test
    void testValidateHeaderSuccess() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String dateStamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String amzDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));

        CanonicalRequest canonical = new CanonicalRequest(
                "GET",
                "/test-bucket/file.txt",
                "",
                Map.of("host", "localhost:9000", "x-amz-date", amzDate),
                List.of("host", "x-amz-date"),
                "UNSIGNED-PAYLOAD"
        );

        String canonicalStr = canonical.toCanonicalString();
        String hashedCanonical = SigV4Validator.sha256Hex(canonicalStr);

        String scope = dateStamp + "/us-east-1/s3/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n" + hashedCanonical;

        byte[] signingKey = SigV4Validator.deriveSigningKey("minis3-secret-key", dateStamp, "us-east-1", "s3");
        String signature = SigV4Validator.hmacSha256Hex(signingKey, stringToSign);

        String authHeader = "AWS4-HMAC-SHA256 Credential=minis3-access-key/" + scope +
                ", SignedHeaders=host;x-amz-date, Signature=" + signature;

        assertDoesNotThrow(() -> validator.validateHeader(authHeader, amzDate, canonical));
    }

    @Test
    void testValidateHeaderInvalidSignatureThrows() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String dateStamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String amzDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));

        CanonicalRequest canonical = new CanonicalRequest(
                "GET",
                "/test-bucket/file.txt",
                "",
                Map.of("host", "localhost:9000"),
                List.of("host"),
                "UNSIGNED-PAYLOAD"
        );

        String badSignature = "0000000000000000000000000000000000000000000000000000000000000000";
        String scope = dateStamp + "/us-east-1/s3/aws4_request";
        String authHeader = "AWS4-HMAC-SHA256 Credential=minis3-access-key/" + scope +
                ", SignedHeaders=host, Signature=" + badSignature;

        assertThrows(SignatureDoesNotMatchException.class, () -> validator.validateHeader(authHeader, amzDate, canonical));
    }
}
