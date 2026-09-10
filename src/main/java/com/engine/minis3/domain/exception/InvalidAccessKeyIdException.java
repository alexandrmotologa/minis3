package com.engine.minis3.domain.exception;

public class InvalidAccessKeyIdException extends MiniS3Exception {
    public InvalidAccessKeyIdException(String accessKey) {
        super("The AWS Access Key Id you provided does not exist in our records: " + accessKey,
                "InvalidAccessKeyId", 403);
    }
}
