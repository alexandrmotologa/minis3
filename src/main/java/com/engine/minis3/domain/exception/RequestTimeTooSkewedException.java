package com.engine.minis3.domain.exception;

public class RequestTimeTooSkewedException extends MiniS3Exception {
    public RequestTimeTooSkewedException(String message) {
        super(message, "RequestTimeTooSkewed", 403);
    }
}
