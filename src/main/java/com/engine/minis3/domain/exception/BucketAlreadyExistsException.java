package com.engine.minis3.domain.exception;

public class BucketAlreadyExistsException extends MiniS3Exception {
    public BucketAlreadyExistsException(String bucketName) {
        super("The requested bucket name is not available: " + bucketName, "BucketAlreadyExists", 409);
    }
}
