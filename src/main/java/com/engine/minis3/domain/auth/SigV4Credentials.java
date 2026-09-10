package com.engine.minis3.domain.auth;

import java.util.Objects;

/**
 * Value object holding AWS SigV4 credentials.
 */
public final class SigV4Credentials {

    private final String accessKey;
    private final String secretKey;
    private final String region;

    public SigV4Credentials(String accessKey, String secretKey, String region) {
        this.accessKey = Objects.requireNonNull(accessKey, "accessKey must not be null");
        this.secretKey = Objects.requireNonNull(secretKey, "secretKey must not be null");
        this.region = (region != null && !region.isBlank()) ? region : "us-east-1";
    }

    public String getAccessKey() {
        return accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getRegion() {
        return region;
    }
}
