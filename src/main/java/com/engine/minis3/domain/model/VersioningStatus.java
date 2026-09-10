package com.engine.minis3.domain.model;

/**
 * S3 Bucket Versioning status.
 */
public enum VersioningStatus {
    OFF("Off"),
    ENABLED("Enabled"),
    SUSPENDED("Suspended");

    private final String value;

    VersioningStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static VersioningStatus fromString(String text) {
        if (text == null || text.isBlank()) {
            return OFF;
        }
        for (VersioningStatus status : values()) {
            if (status.value.equalsIgnoreCase(text) || status.name().equalsIgnoreCase(text)) {
                return status;
            }
        }
        return OFF;
    }
}
