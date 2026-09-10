package com.engine.minis3.domain.exception;

public class NoSuchUploadException extends MiniS3Exception {
    public NoSuchUploadException(String uploadId) {
        super("The specified multipart upload does not exist: " + uploadId, "NoSuchUpload", 404);
    }
}
