package com.engine.minis3.infrastructure.adapter.in.rest;

import com.engine.minis3.application.dto.CompleteMultipartUploadRequest;
import com.engine.minis3.application.dto.CompleteMultipartUploadResult;
import com.engine.minis3.application.dto.InitiateMultipartUploadResult;
import com.engine.minis3.application.dto.ListPartsResult;
import com.engine.minis3.application.service.MultipartService;
import com.engine.minis3.domain.model.MultipartPart;
import com.engine.minis3.domain.model.MultipartUpload;
import com.engine.minis3.domain.model.S3Object;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
public class S3MultipartController {

    private final MultipartService multipartService;

    public S3MultipartController(MultipartService multipartService) {
        this.multipartService = multipartService;
    }

    /**
     * Initiate multipart upload: POST /{bucket}/{key}?uploads
     */
    @PostMapping(value = "/{bucket}/{*key}", params = "uploads", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<InitiateMultipartUploadResult> initiateMultipartUpload(
            @PathVariable("bucket") String bucket,
            @PathVariable("key") String rawKey,
            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType) {

        String key = cleanKey(rawKey);
        MultipartUpload upload = multipartService.initiateMultipartUpload(bucket, key, contentType);

        InitiateMultipartUploadResult result = new InitiateMultipartUploadResult(
                upload.getBucketName(),
                upload.getKey(),
                upload.getUploadId()
        );

        return ResponseEntity.ok(result);
    }

    /**
     * Upload part: PUT /{bucket}/{key}?uploadId={id}&partNumber={num}
     */
    @PutMapping(value = "/{bucket}/{*key}", params = {"uploadId", "partNumber"})
    public ResponseEntity<Void> uploadPart(
            @PathVariable("bucket") String bucket,
            @PathVariable("key") String rawKey,
            @RequestParam("uploadId") String uploadId,
            @RequestParam("partNumber") int partNumber,
            @RequestHeader(value = HttpHeaders.CONTENT_LENGTH, required = false, defaultValue = "-1") long contentLength,
            @RequestHeader(value = "Content-MD5", required = false) String contentMd5,
            HttpServletRequest request) throws IOException {

        MultipartPart part = multipartService.uploadPart(
                uploadId,
                partNumber,
                request.getInputStream(),
                contentLength,
                contentMd5
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, "\"" + part.getEtag() + "\"")
                .build();
    }

    /**
     * Complete multipart upload: POST /{bucket}/{key}?uploadId={id}
     */
    @PostMapping(value = "/{bucket}/{*key}", params = "uploadId",
            consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE},
            produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<CompleteMultipartUploadResult> completeMultipartUpload(
            @PathVariable("bucket") String bucket,
            @PathVariable("key") String rawKey,
            @RequestParam("uploadId") String uploadId,
            @RequestBody CompleteMultipartUploadRequest completeRequest) {

        String key = cleanKey(rawKey);
        S3Object s3Object = multipartService.completeMultipartUpload(uploadId, completeRequest.getParts());

        CompleteMultipartUploadResult result = new CompleteMultipartUploadResult(
                "/" + bucket + "/" + key,
                bucket,
                key,
                s3Object.getEtag()
        );

        return ResponseEntity.ok(result);
    }

    /**
     * Abort multipart upload: DELETE /{bucket}/{key}?uploadId={id}
     */
    @DeleteMapping(value = "/{bucket}/{*key}", params = "uploadId")
    public ResponseEntity<Void> abortMultipartUpload(
            @PathVariable("bucket") String bucket,
            @PathVariable("key") String rawKey,
            @RequestParam("uploadId") String uploadId) {

        multipartService.abortMultipartUpload(uploadId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * List parts: GET /{bucket}/{key}?uploadId={id}
     */
    @GetMapping(value = "/{bucket}/{*key}", params = "uploadId", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<ListPartsResult> listParts(
            @PathVariable("bucket") String bucket,
            @PathVariable("key") String rawKey,
            @RequestParam("uploadId") String uploadId) {

        String key = cleanKey(rawKey);
        List<MultipartPart> parts = multipartService.listParts(uploadId);

        List<ListPartsResult.PartSummaryDto> dtos = parts.stream()
                .map(p -> new ListPartsResult.PartSummaryDto(
                        p.getPartNumber(),
                        p.getUploadedAt().toString(),
                        p.getEtag(),
                        p.getSize()
                ))
                .toList();

        ListPartsResult result = new ListPartsResult(bucket, key, uploadId, dtos);
        return ResponseEntity.ok(result);
    }

    private String cleanKey(String rawKey) {
        if (rawKey == null) return "";
        return rawKey.startsWith("/") ? rawKey.substring(1) : rawKey;
    }
}
