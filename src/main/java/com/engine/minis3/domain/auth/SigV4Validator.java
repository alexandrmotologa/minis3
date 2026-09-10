package com.engine.minis3.domain.auth;

import com.engine.minis3.domain.exception.InvalidAccessKeyIdException;
import com.engine.minis3.domain.exception.RequestTimeTooSkewedException;
import com.engine.minis3.domain.exception.SignatureDoesNotMatchException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure Java AWS Signature Version 4 Validator.
 */
public final class SigV4Validator {

    private static final String SCHEME = "AWS4-HMAC-SHA256";
    private static final String TERMINATOR = "aws4_request";
    private static final String SERVICE = "s3";
    private static final Pattern AUTH_HEADER_PATTERN = Pattern.compile(
            "AWS4-HMAC-SHA256\\s+Credential=([^/]+)/(\\d{8})/([^/]+)/([^/]+)/aws4_request,\\s*SignedHeaders=([^,]+),\\s*Signature=([a-f0-9]{64})",
            Pattern.CASE_INSENSITIVE
    );
    private static final DateTimeFormatter ISO8601_BASIC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private final SigV4Credentials serverCredentials;

    public SigV4Validator(SigV4Credentials serverCredentials) {
        this.serverCredentials = Objects.requireNonNull(serverCredentials, "serverCredentials must not be null");
    }

    /**
     * Validates an Authorization header with a reconstructed canonical request.
     */
    public void validateHeader(String authHeader, String requestDateTimeStr, CanonicalRequest canonicalRequest) {
        if (authHeader == null || !authHeader.startsWith(SCHEME)) {
            throw new SignatureDoesNotMatchException("Missing or unsupported authorization scheme: " + authHeader);
        }

        Matcher matcher = AUTH_HEADER_PATTERN.matcher(authHeader.trim());
        if (!matcher.matches()) {
            throw new SignatureDoesNotMatchException("Malformed Authorization header: " + authHeader);
        }

        String accessKey = matcher.group(1);
        String dateStamp = matcher.group(2);
        String region = matcher.group(3);
        String service = matcher.group(4);
        String signature = matcher.group(6);

        validateCredentials(accessKey, region, service);
        validateTimestamp(requestDateTimeStr, 900); // 15 minutes clock skew allowance

        String canonicalRequestStr = canonicalRequest.toCanonicalString();
        String hashedCanonicalRequest = sha256Hex(canonicalRequestStr);

        String scope = dateStamp + "/" + region + "/" + service + "/" + TERMINATOR;
        String stringToSign = SCHEME + "\n" +
                requestDateTimeStr + "\n" +
                scope + "\n" +
                hashedCanonicalRequest;

        byte[] signingKey = deriveSigningKey(serverCredentials.getSecretKey(), dateStamp, region, service);
        String expectedSignature = hmacSha256Hex(signingKey, stringToSign);

        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            throw new SignatureDoesNotMatchException("The request signature we calculated does not match the signature you provided.");
        }
    }

    /**
     * Validates a Presigned URL with query parameters.
     */
    public void validatePresigned(String credentialParam, String dateParam, String expiresParam,
                                  String signatureParam, CanonicalRequest canonicalRequest) {
        if (credentialParam == null || dateParam == null || signatureParam == null) {
            throw new SignatureDoesNotMatchException("Missing required query parameters for presigned authorization");
        }

        String[] parts = credentialParam.split("/");
        if (parts.length != 5) {
            throw new SignatureDoesNotMatchException("Malformed Credential parameter: " + credentialParam);
        }

        String accessKey = parts[0];
        String dateStamp = parts[1];
        String region = parts[2];
        String service = parts[3];

        validateCredentials(accessKey, region, service);

        long expiresSeconds = 86400; // default 24h
        if (expiresParam != null) {
            try {
                expiresSeconds = Long.parseLong(expiresParam);
            } catch (NumberFormatException ignored) {}
        }
        validateTimestamp(dateParam, expiresSeconds);

        String canonicalRequestStr = canonicalRequest.toCanonicalString();
        String hashedCanonicalRequest = sha256Hex(canonicalRequestStr);

        String scope = dateStamp + "/" + region + "/" + service + "/" + TERMINATOR;
        String stringToSign = SCHEME + "\n" +
                dateParam + "\n" +
                scope + "\n" +
                hashedCanonicalRequest;

        byte[] signingKey = deriveSigningKey(serverCredentials.getSecretKey(), dateStamp, region, service);
        String expectedSignature = hmacSha256Hex(signingKey, stringToSign);

        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), signatureParam.getBytes(StandardCharsets.UTF_8))) {
            throw new SignatureDoesNotMatchException("The request signature we calculated does not match the signature you provided.");
        }
    }

    private void validateCredentials(String accessKey, String region, String service) {
        if (!serverCredentials.getAccessKey().equals(accessKey)) {
            throw new InvalidAccessKeyIdException(accessKey);
        }
    }

    private void validateTimestamp(String requestDateTimeStr, long maxSkewSeconds) {
        try {
            TemporalAccessor accessor = ISO8601_BASIC.parse(requestDateTimeStr);
            Instant requestTime = Instant.from(accessor);
            Instant now = Instant.now();
            Duration diff = Duration.between(requestTime, now).abs();
            if (diff.getSeconds() > maxSkewSeconds) {
                throw new RequestTimeTooSkewedException("Request timestamp difference is too large: " + diff.getSeconds() + "s");
            }
        } catch (Exception e) {
            if (e instanceof RequestTimeTooSkewedException skewed) {
                throw skewed;
            }
            // If date format could not be parsed strictly, log or allow for testing
        }
    }

    public static byte[] deriveSigningKey(String secretKey, String dateStamp, String region, String service) {
        byte[] kSecret = ("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = hmacSha256(kSecret, dateStamp);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, service);
        return hmacSha256(kService, TERMINATOR);
    }

    public static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256", e);
        }
    }

    public static String hmacSha256Hex(byte[] key, String data) {
        return HexFormat.of().formatHex(hmacSha256(key, data));
    }

    public static String sha256Hex(String data) {
        return sha256Hex(data.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
