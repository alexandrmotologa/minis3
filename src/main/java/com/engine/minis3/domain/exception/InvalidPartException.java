package com.engine.minis3.domain.exception;

public class InvalidPartException extends MiniS3Exception {
    public InvalidPartException(String message) {
        super(message, "InvalidPart", 400);
    }
}
