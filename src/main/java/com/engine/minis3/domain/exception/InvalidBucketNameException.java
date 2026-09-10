package com.engine.minis3.domain.exception;

public class InvalidBucketNameException extends MiniS3Exception {
    public InvalidBucketNameException(String message) {
        super(message, "InvalidBucketName", 400);
    }
}
