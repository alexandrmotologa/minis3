package com.engine.minis3.domain.model;

import java.util.Objects;

/**
 * Value object representing an object key and optional version identifier for targeted operations.
 */
public record ObjectKeyVersion(String key, String versionId) {

    public ObjectKeyVersion {
        Objects.requireNonNull(key, "key must not be null");
    }

    public ObjectKeyVersion(String key) {
        this(key, null);
    }

    public boolean hasVersion() {
        return versionId != null && !versionId.isBlank() && !"null".equalsIgnoreCase(versionId);
    }
}
