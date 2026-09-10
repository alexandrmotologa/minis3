package com.engine.minis3.domain.exception;

public class NoSuchBucketException extends MiniS3Exception {
    public NoSuchBucketException(String bucketName) {
        super("The specified bucket does not exist: " + bucketName, "NoSuchBucket", 404);
    }
}
