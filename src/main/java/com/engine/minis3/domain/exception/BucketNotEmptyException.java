package com.engine.minis3.domain.exception;

public class BucketNotEmptyException extends MiniS3Exception {
    public BucketNotEmptyException(String bucketName) {
        super("The bucket you tried to delete is not empty: " + bucketName, "BucketNotEmpty", 409);
    }
}
