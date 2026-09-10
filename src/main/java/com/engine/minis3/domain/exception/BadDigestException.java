package com.engine.minis3.domain.exception;

public class BadDigestException extends MiniS3Exception {
    public BadDigestException(String message) {
        super(message, "BadDigest", 400);
    }
}
