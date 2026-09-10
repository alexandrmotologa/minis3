package com.engine.minis3.application.event;

import java.time.Instant;

/**
 * Immutable record representing an audit/telemetry activity event in MiniS3.
 */
public record S3ActivityEvent(
        String id,
        String type,
        String bucket,
        String key,
        String details,
        Instant timestamp
) {}
