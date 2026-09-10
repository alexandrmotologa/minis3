package com.engine.minis3.domain.exception;

/**
 * Base unchecked exception for MiniS3 domain errors.
 */
public abstract class MiniS3Exception extends RuntimeException {

    private final String errorCode;
    private final int httpStatusCode;

    protected MiniS3Exception(String message, String errorCode, int httpStatusCode) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatusCode = httpStatusCode;
    }

    protected MiniS3Exception(String message, Throwable cause, String errorCode, int httpStatusCode) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatusCode = httpStatusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getHttpStatusCode() {
        return httpStatusCode;
    }
}
