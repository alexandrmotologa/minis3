package com.engine.minis3.infrastructure.adapter.in.rest;

import com.engine.minis3.application.dto.*;
import com.engine.minis3.application.service.BucketService;
import com.engine.minis3.application.service.ObjectService;
import com.engine.minis3.domain.model.Bucket;
import com.engine.minis3.domain.model.ObjectKeyVersion;
import com.engine.minis3.domain.model.S3Object;
import com.engine.minis3.domain.model.VersioningStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
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
     * Get bucket versioning configuration: GET /{bucket}?versioning
     */
    @GetMapping(value = "/{bucket}", params = "versioning", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<VersioningConfiguration> getBucketVersioning(@PathVariable("bucket") String bucket) {
        VersioningStatus status = bucketService.getVersioning(bucket);
        String xmlStatus = (status == VersioningStatus.ENABLED) ? "Enabled"
                : (status == VersioningStatus.SUSPENDED) ? "Suspended" : null;
        return ResponseEntity.ok(new VersioningConfiguration(xmlStatus));
    }

    /**
     * Set bucket versioning configuration: PUT /{bucket}?versioning
     */
    @PutMapping(value = "/{bucket}", params = "versioning", consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE})
    public ResponseEntity<Void> putBucketVersioning(
            @PathVariable("bucket") String bucket,
            @RequestBody VersioningConfiguration config) {
        VersioningStatus status = VersioningStatus.fromString(config != null ? config.getStatus() : null);
        bucketService.setVersioning(bucket, status);
        return ResponseEntity.ok().build();
    }

    /**
     * List object versions in bucket: GET /{bucket}?versions
     */
    @GetMapping(value = "/{bucket}", params = "versions", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<ListVersionsResult> listObjectVersions(
            @PathVariable("bucket") String bucket,
            @RequestParam(value = "prefix", required = false, defaultValue = "") String prefix,
            @RequestParam(value = "key-marker", required = false) String keyMarker,
            @RequestParam(value = "version-id-marker", required = false) String versionIdMarker,
            @RequestParam(value = "max-keys", required = false, defaultValue = "1000") int maxKeys) {

        bucketService.getBucket(bucket);
        List<S3Object> objects = objectService.listObjectVersions(bucket, prefix, keyMarker, versionIdMarker, maxKeys);

        ListVersionsResult result = new ListVersionsResult(bucket, prefix, keyMarker, versionIdMarker, maxKeys, objects.size() >= maxKeys);

        for (S3Object obj : objects) {
            if (obj.isDeleteMarker()) {
                result.getDeleteMarkers().add(new ListVersionsResult.DeleteMarkerEntry(
                        obj.getKey(),
                        obj.getVersionId(),
                        obj.isLatest(),
                        obj.getCreatedAt().toString()
                ));
            } else {
                result.getVersions().add(new ListVersionsResult.VersionEntry(
                        obj.getKey(),
                        obj.getVersionId(),
                        obj.isLatest(),
                        obj.getCreatedAt().toString(),
                        obj.getEtag(),
                        obj.getSize()
                ));
            }
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Multi-Object Delete: POST /{bucket}?delete
     */
    @PostMapping(value = "/{bucket}", params = "delete", consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE}, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<DeleteObjectsResult> deleteMultipleObjects(
            @PathVariable("bucket") String bucket,
            @RequestBody DeleteObjectsRequest request) {

        bucketService.getBucket(bucket);
        DeleteObjectsResult result = new DeleteObjectsResult();

        if (request != null && request.getObjects() != null) {
            List<ObjectKeyVersion> targets = new ArrayList<>();
            for (DeleteObjectsRequest.ObjectIdentifier objId : request.getObjects()) {
                targets.add(new ObjectKeyVersion(objId.getKey(), objId.getVersionId()));
            }

            objectService.deleteBatch(bucket, targets);

            if (!request.getQuiet()) {
                for (DeleteObjectsRequest.ObjectIdentifier objId : request.getObjects()) {
                    result.getDeleted().add(new DeleteObjectsResult.DeletedObject(
                            objId.getKey(),
                            objId.getVersionId(),
                            false
                    ));
                }
            }
        }

        return ResponseEntity.ok(result);
    }

    /**
     * List objects in bucket: GET /{bucket}
     */
    @GetMapping(value = "/{bucket}", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<ListBucketResult> listObjects(
            @PathVariable("bucket") String bucket,
            @RequestParam(value = "prefix", required = false, defaultValue = "") String prefix,
            @RequestParam(value = "continuation-token", required = false) String continuationToken,
            @RequestParam(value = "max-keys", required = false, defaultValue = "1000") int maxKeys) {

        bucketService.getBucket(bucket);
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
