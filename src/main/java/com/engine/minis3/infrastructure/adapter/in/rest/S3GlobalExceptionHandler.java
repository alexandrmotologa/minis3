package com.engine.minis3.infrastructure.adapter.in.rest;

import com.engine.minis3.application.dto.ErrorResponse;
import com.engine.minis3.domain.exception.MiniS3Exception;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class S3GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(S3GlobalExceptionHandler.class);

    @ExceptionHandler(MiniS3Exception.class)
    public ResponseEntity<ErrorResponse> handleMiniS3Exception(MiniS3Exception ex, HttpServletRequest request) {
        log.warn("S3 Exception [{}] on {}: {}", ex.getErrorCode(), request.getRequestURI(), ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(ex.getErrorCode(), ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(ex.getHttpStatusCode())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
                .body(errorResponse);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument on {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse("InvalidArgument", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.badRequest()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
                .body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        ErrorResponse errorResponse = new ErrorResponse("InternalError", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.internalServerError()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
                .body(errorResponse);
    }
}
