package com.engine.minis3.infrastructure.adapter.in.rest;

import com.engine.minis3.application.dto.ListAllMyBucketsResult;
import com.engine.minis3.application.dto.ListBucketResult;
import com.engine.minis3.application.service.BucketService;
import com.engine.minis3.application.service.ObjectService;
import com.engine.minis3.domain.model.Bucket;
import com.engine.minis3.domain.model.S3Object;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class S3BucketController {

    private final BucketService bucketService;
    private final ObjectService objectService;

    public S3BucketController(BucketService bucketService, ObjectService objectService) {
        this.bucketService = bucketService;
        this.objectService = objectService;
    }

    /**
     * List all buckets: GET /
     * Dispatched when Accept contains xml or AWS headers present.
     */
    @GetMapping(value = "/", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<ListAllMyBucketsResult> listAllMyBuckets(HttpServletRequest request) {
        List<Bucket> buckets = bucketService.listBuckets();
        List<ListAllMyBucketsResult.BucketDto> dtos = buckets.stream()
                .map(b -> new ListAllMyBucketsResult.BucketDto(b.getName(), b.getCreatedAt().toString()))
                .toList();

        return ResponseEntity.ok(new ListAllMyBucketsResult(dtos));
    }

    /**
     * Create bucket: PUT /{bucket}
     */
    @PutMapping(value = "/{bucket}")
    public ResponseEntity<Void> createBucket(@PathVariable("bucket") String bucket) {
        bucketService.createBucket(bucket);
        return ResponseEntity.ok()
                .header(HttpHeaders.LOCATION, "/" + bucket)
                .build();
    }

    /**
     * Head bucket: HEAD /{bucket}
     */
    @RequestMapping(value = "/{bucket}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> headBucket(@PathVariable("bucket") String bucket) {
        bucketService.getBucket(bucket);
        return ResponseEntity.ok().build();
    }

    /**
     * Delete bucket: DELETE /{bucket}
     */
    @DeleteMapping(value = "/{bucket}")
    public ResponseEntity<Void> deleteBucket(@PathVariable("bucket") String bucket) {
        bucketService.deleteBucket(bucket);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * List objects in bucket: GET /{bucket}?list-type=2
     */
    @GetMapping(value = "/{bucket}", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<ListBucketResult> listObjects(
            @PathVariable("bucket") String bucket,
            @RequestParam(value = "prefix", required = false, defaultValue = "") String prefix,
            @RequestParam(value = "continuation-token", required = false) String continuationToken,
            @RequestParam(value = "max-keys", required = false, defaultValue = "1000") int maxKeys) {

        bucketService.getBucket(bucket); // Verify bucket exists
        List<S3Object> objects = objectService.listObjects(bucket, prefix, continuationToken, maxKeys);

        List<ListBucketResult.S3ObjectSummaryDto> contents = objects.stream()
                .map(o -> new ListBucketResult.S3ObjectSummaryDto(
                        o.getKey(),
                        o.getCreatedAt().toString(),
                        o.getEtag(),
                        o.getSize()
                ))
                .toList();

        boolean isTruncated = objects.size() >= maxKeys;
        String nextToken = isTruncated && !objects.isEmpty() ? objects.get(objects.size() - 1).getKey() : null;

        ListBucketResult result = new ListBucketResult(
                bucket,
                prefix,
                objects.size(),
                maxKeys,
                isTruncated,
                nextToken,
                contents
        );

        return ResponseEntity.ok(result);
    }
}
