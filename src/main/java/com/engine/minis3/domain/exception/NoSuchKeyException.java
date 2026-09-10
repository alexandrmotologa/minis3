package com.engine.minis3.domain.exception;

public class NoSuchKeyException extends MiniS3Exception {
    public NoSuchKeyException(String key) {
        super("The specified key does not exist: " + key, "NoSuchKey", 404);
    }
}
