package com.engine.minis3.domain.exception;

public class SignatureDoesNotMatchException extends MiniS3Exception {
    public SignatureDoesNotMatchException(String message) {
        super(message, "SignatureDoesNotMatch", 403);
    }
}
