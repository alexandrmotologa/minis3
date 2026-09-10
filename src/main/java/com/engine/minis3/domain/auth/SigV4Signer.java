package com.engine.minis3.domain.auth;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Generator for AWS Signature Version 4 Presigned URLs.
 */
public final class SigV4Signer {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_STAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private SigV4Signer() {}

    /**
     * Generates a fully signed S3 Presigned URL.
     */
    public static String generatePresignedUrl(
            String endpoint,
            String httpMethod,
            String bucket,
            String key,
            String accessKey,
            String secretKey,
            String region,
            long expiresInSeconds) {

        Instant now = Instant.now();
        String amzDate = DATE_FORMATTER.format(now);
        String dateStamp = DATE_STAMP_FORMATTER.format(now);

        String scope = dateStamp + "/" + region + "/s3/aws4_request";
        String normalizedEndpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        String host = extractHost(normalizedEndpoint);

        String canonicalUri = "/" + urlEncodePath(bucket) + "/" + urlEncodePath(key);

        // Canonical query parameters must be sorted alphabetically by key
        String credParam = urlEncode(accessKey + "/" + scope);
        String queryWithoutSig = "X-Amz-Algorithm=AWS4-HMAC-SHA256" +
                "&X-Amz-Credential=" + credParam +
                "&X-Amz-Date=" + amzDate +
                "&X-Amz-Expires=" + expiresInSeconds +
                "&X-Amz-SignedHeaders=host";

        String canonicalHeaders = "host:" + host + "\n";
        String signedHeaders = "host";
        String payloadHash = "UNSIGNED-PAYLOAD";

        String canonicalRequest = httpMethod.toUpperCase() + "\n" +
                canonicalUri + "\n" +
                queryWithoutSig + "\n" +
                canonicalHeaders + "\n" +
                signedHeaders + "\n" +
                payloadHash;

        String stringToSign = "AWS4-HMAC-SHA256\n" +
                amzDate + "\n" +
                scope + "\n" +
                DigestUtils.sha256Hex(canonicalRequest);

        byte[] signingKey = deriveSigningKey(secretKey, dateStamp, region, "s3");
        String signature = Hex.encodeHexString(hmacSha256(signingKey, stringToSign));

        return normalizedEndpoint + canonicalUri + "?" + queryWithoutSig + "&X-Amz-Signature=" + signature;
    }

    private static String extractHost(String endpoint) {
        String noScheme = endpoint.replaceFirst("^[a-zA-Z]+://", "");
        int slashIdx = noScheme.indexOf('/');
        return (slashIdx != -1) ? noScheme.substring(0, slashIdx) : noScheme;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String urlEncodePath(String path) {
        if (path == null) return "";
        String[] segments = path.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) sb.append("/");
            sb.append(urlEncode(segments[i]));
        }
        return sb.toString();
    }

    private static byte[] deriveSigningKey(String key, String dateStamp, String regionName, String serviceName) {
        byte[] kSecret = ("AWS4" + key).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = hmacSha256(kSecret, dateStamp);
        byte[] kRegion = hmacSha256(kDate, regionName);
        byte[] kService = hmacSha256(kRegion, serviceName);
        return hmacSha256(kService, "aws4_request");
    }

    private static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256", e);
        }
    }
}
