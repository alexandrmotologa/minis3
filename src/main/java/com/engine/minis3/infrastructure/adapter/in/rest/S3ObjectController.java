package com.engine.minis3.infrastructure.adapter.in.rest;

import com.engine.minis3.application.service.ObjectService;
import com.engine.minis3.domain.exception.NoSuchKeyException;
import com.engine.minis3.domain.model.S3Object;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
public class S3ObjectController {

    private static final Pattern RANGE_PATTERN = Pattern.compile("^bytes=(\\d+)-(\\d*)$");

    private final ObjectService objectService;

    public S3ObjectController(ObjectService objectService) {
        this.objectService = objectService;
    }

    /**
     * Upload an object: PUT /{bucket}/{key}
     */
    @PutMapping(value = "/{bucket}/{*key}", params = "!uploadId")
    public ResponseEntity<Void> putObject(
            @PathVariable("bucket") String bucket,
            @PathVariable("key") String rawKey,
            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
            @RequestHeader(value = HttpHeaders.CONTENT_LENGTH, required = false, defaultValue = "-1") long contentLength,
            @RequestHeader(value = "Content-MD5", required = false) String contentMd5,
            HttpServletRequest request) throws IOException {

        String key = cleanKey(rawKey);
        S3Object object = objectService.putObject(
                bucket,
                key,
                contentType,
                request.getInputStream(),
                contentLength,
                contentMd5
        );

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.ETAG, "\"" + object.getEtag() + "\"");

        if (object.getVersionId() != null && !"null".equalsIgnoreCase(object.getVersionId())) {
            builder.header("x-amz-version-id", object.getVersionId());
        }

        return builder.build();
    }

    /**
     * Retrieve an object: GET /{bucket}/{key}
     */
    @GetMapping(value = "/{bucket}/{*key}", params = "!uploadId")
    public void getObject(
            @PathVariable("bucket") String bucket,
            @PathVariable("key") String rawKey,
            @RequestParam(value = "versionId", required = false) String versionId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
            jakarta.servlet.http.HttpServletResponse response) throws IOException {

        String key = cleanKey(rawKey);
        S3Object object = objectService.getObject(bucket, key, versionId);

        response.setHeader(HttpHeaders.ETAG, "\"" + object.getEtag() + "\"");
        response.setHeader(HttpHeaders.LAST_MODIFIED, DateTimeFormatter.RFC_1123_DATE_TIME.format(object.getCreatedAt().atZone(java.time.ZoneOffset.UTC)));
        if (object.getVersionId() != null && !"null".equalsIgnoreCase(object.getVersionId())) {
            response.setHeader("x-amz-version-id", object.getVersionId());
        }

        if (rangeHeader != null && !rangeHeader.isBlank()) {
            handleRangeRequest(object, rangeHeader, response);
            return;
        }

        response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_OK);
        response.setContentType(object.getContentType());
        response.setContentLengthLong(object.getSize());
        objectService.streamObject(object, response.getOutputStream());
    }

    /**
     * Check object existence and metadata: HEAD /{bucket}/{key}
     */
    @RequestMapping(value = "/{bucket}/{*key}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> headObject(
            @PathVariable("bucket") String bucket,
            @PathVariable("key") String rawKey,
            @RequestParam(value = "versionId", required = false) String versionId) {

        String key = cleanKey(rawKey);
        Optional<S3Object> objOpt = objectService.headObject(bucket, key, versionId);
        if (objOpt.isEmpty()) {
            throw new NoSuchKeyException(key);
        }

        S3Object object = objOpt.get();
        if (object.isDeleteMarker()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .header("x-amz-delete-marker", "true")
                    .header("x-amz-version-id", object.getVersionId())
                    .build();
        }

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.ETAG, "\"" + object.getEtag() + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(object.getSize()))
                .header(HttpHeaders.CONTENT_TYPE, object.getContentType())
                .header(HttpHeaders.LAST_MODIFIED, DateTimeFormatter.RFC_1123_DATE_TIME.format(object.getCreatedAt().atZone(java.time.ZoneOffset.UTC)));

        if (object.getVersionId() != null && !"null".equalsIgnoreCase(object.getVersionId())) {
            builder.header("x-amz-version-id", object.getVersionId());
        }

        return builder.build();
    }

    /**
     * Delete an object or version: DELETE /{bucket}/{key}
     */
    @DeleteMapping(value = "/{bucket}/{*key}", params = "!uploadId")
    public ResponseEntity<Void> deleteObject(
            @PathVariable("bucket") String bucket,
            @PathVariable("key") String rawKey,
            @RequestParam(value = "versionId", required = false) String versionId) {

        String key = cleanKey(rawKey);
        objectService.deleteObject(bucket, key, versionId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private void handleRangeRequest(S3Object object, String rangeHeader, jakarta.servlet.http.HttpServletResponse response) throws IOException {
        Matcher matcher = RANGE_PATTERN.matcher(rangeHeader.trim());
        if (!matcher.matches()) {
            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            response.setHeader("Content-Range", "bytes */" + object.getSize());
            return;
        }

        long start = Long.parseLong(matcher.group(1));
        long end = matcher.group(2).isEmpty() ? object.getSize() - 1 : Long.parseLong(matcher.group(2));

        if (start >= object.getSize() || start > end) {
            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            response.setHeader("Content-Range", "bytes */" + object.getSize());
            return;
        }

        end = Math.min(end, object.getSize() - 1);
        byte[] data = objectService.readRange(object, start, end);

        response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_PARTIAL_CONTENT);
        response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + object.getSize());
        response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(data.length));
        response.setContentType(object.getContentType());
        response.getOutputStream().write(data);
        response.getOutputStream().flush();
    }

    private String cleanKey(String rawKey) {
        if (rawKey == null) return "";
        return rawKey.startsWith("/") ? rawKey.substring(1) : rawKey;
    }
}
