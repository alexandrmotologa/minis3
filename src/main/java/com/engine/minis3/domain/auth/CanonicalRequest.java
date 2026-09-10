package com.engine.minis3.domain.auth;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Representation of a reconstructed AWS Canonical Request.
 */
public final class CanonicalRequest {

    private final String httpMethod;
    private final String canonicalUri;
    private final String canonicalQueryString;
    private final Map<String, String> headers;
    private final List<String> signedHeaderNames;
    private final String payloadHash;

    public CanonicalRequest(String httpMethod, String canonicalUri, String canonicalQueryString,
                            Map<String, String> headers, List<String> signedHeaderNames, String payloadHash) {
        this.httpMethod = Objects.requireNonNull(httpMethod, "httpMethod must not be null").toUpperCase();
        this.canonicalUri = (canonicalUri != null && !canonicalUri.isBlank()) ? canonicalUri : "/";
        this.canonicalQueryString = (canonicalQueryString != null) ? canonicalQueryString : "";
        this.headers = (headers != null) ? Map.copyOf(headers) : Collections.emptyMap();
        this.signedHeaderNames = (signedHeaderNames != null) ? List.copyOf(signedHeaderNames) : Collections.emptyList();
        if (payloadHash != null) {
            String trimmed = payloadHash.trim();
            if (trimmed.equalsIgnoreCase("UNSIGNED-PAYLOAD") || trimmed.equalsIgnoreCase("STREAMING-AWS4-HMAC-SHA256-PAYLOAD")) {
                this.payloadHash = trimmed.toUpperCase();
            } else {
                this.payloadHash = trimmed.toLowerCase();
            }
        } else {
            this.payloadHash = "UNSIGNED-PAYLOAD";
        }
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getCanonicalUri() {
        return canonicalUri;
    }

    public String getCanonicalQueryString() {
        return canonicalQueryString;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public List<String> getSignedHeaderNames() {
        return signedHeaderNames;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public String buildCanonicalHeadersString() {
        StringBuilder sb = new StringBuilder();
        for (String headerName : signedHeaderNames) {
            String value = headers.get(headerName);
            if (value != null) {
                sb.append(headerName.toLowerCase()).append(':').append(value.trim()).append('\n');
            }
        }
        return sb.toString();
    }

    public String buildSignedHeadersString() {
        return String.join(";", signedHeaderNames);
    }

    public String toCanonicalString() {
        return httpMethod + "\n" +
                canonicalUri + "\n" +
                canonicalQueryString + "\n" +
                buildCanonicalHeadersString() + "\n" +
                buildSignedHeadersString() + "\n" +
                payloadHash;
    }
}
