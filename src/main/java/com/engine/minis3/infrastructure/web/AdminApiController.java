package com.engine.minis3.infrastructure.web;

import com.engine.minis3.application.service.AdminService;
import com.engine.minis3.application.service.BucketService;
import com.engine.minis3.application.service.ObjectService;
import com.engine.minis3.application.service.StorageScrubberService;
import com.engine.minis3.domain.auth.SigV4Signer;
import com.engine.minis3.domain.model.ApiCredential;
import com.engine.minis3.domain.model.Bucket;
import com.engine.minis3.domain.model.S3Object;
import com.engine.minis3.domain.model.ScrubReport;
import com.engine.minis3.domain.model.StorageStats;
import com.engine.minis3.domain.model.VersioningStatus;
import com.engine.minis3.domain.port.CredentialRepositoryPort;
import com.engine.minis3.infrastructure.config.MiniS3Properties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminApiController {

    private final AdminService adminService;
    private final BucketService bucketService;
    private final ObjectService objectService;
    private final StorageScrubberService scrubberService;
    private final CredentialRepositoryPort credentialRepository;
    private final com.engine.minis3.application.event.S3EventPublisher eventPublisher;
    private final MiniS3Properties properties;

    public AdminApiController(AdminService adminService,
                              BucketService bucketService,
                              ObjectService objectService,
                              StorageScrubberService scrubberService,
                              CredentialRepositoryPort credentialRepository,
                              com.engine.minis3.application.event.S3EventPublisher eventPublisher,
                              MiniS3Properties properties) {
        this.adminService = adminService;
        this.bucketService = bucketService;
        this.objectService = objectService;
        this.scrubberService = scrubberService;
        this.credentialRepository = credentialRepository;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    @GetMapping("/stats")
    public ResponseEntity<StorageStats> getStats() {
        return ResponseEntity.ok(adminService.getStats());
    }

    @PostMapping("/gc")
    public ResponseEntity<Map<String, Object>> runGarbageCollection() {
        long reclaimed = adminService.runGarbageCollection();
        Map<String, Object> resp = new HashMap<>();
        resp.put("reclaimedChunks", reclaimed);
        resp.put("status", "success");
        eventPublisher.publish("GC_RUN", null, null, "Vacuum GC reclaimed " + reclaimed + " unreferenced chunks");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/scrub")
    public ResponseEntity<ScrubReport> runIntegrityScrub() {
        ScrubReport report = scrubberService.runScrub();
        eventPublisher.publish("SCRUB_REPORT", null, null,
                "Audited " + report.totalChunks() + " chunks (" + report.corruptedChunks() + " corrupted)");
        return ResponseEntity.ok(report);
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents() {
        return eventPublisher.subscribe();
    }

    @PostMapping("/presign")
    public ResponseEntity<Map<String, String>> generatePresignedUrl(
            @RequestParam("bucket") String bucket,
            @RequestParam("key") String key,
            @RequestParam(value = "method", defaultValue = "GET") String method,
            @RequestParam(value = "expiresIn", defaultValue = "3600") long expiresInSeconds,
            HttpServletRequest request) {

        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        String endpoint = scheme + "://" + host + (port == 80 || port == 443 ? "" : ":" + port);

        String accessKey = properties.getAuth().getAccessKey();
        String secretKey = properties.getAuth().getSecretKey();
        String region = properties.getAuth().getRegion();

        String url = SigV4Signer.generatePresignedUrl(
                endpoint,
                method,
                bucket,
                key,
                accessKey,
                secretKey,
                region,
                expiresInSeconds
        );

        Map<String, String> response = new HashMap<>();
        response.put("url", url);
        response.put("expiresInSeconds", String.valueOf(expiresInSeconds));
        response.put("method", method.toUpperCase());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/credentials")
    public ResponseEntity<List<ApiCredential>> listCredentials() {
        return ResponseEntity.ok(credentialRepository.listCredentials());
    }

    @PostMapping("/credentials")
    public ResponseEntity<ApiCredential> createCredential(
            @RequestParam(value = "role", defaultValue = "READ_WRITE") String roleStr,
            @RequestParam(value = "allowedBuckets", defaultValue = "*") String allowedBuckets) {

        String accessKey = "AKI" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        String secretKey = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        ApiCredential cred = new ApiCredential(
                accessKey,
                secretKey,
                ApiCredential.Role.fromString(roleStr),
                allowedBuckets,
                Instant.now()
        );
        credentialRepository.saveCredential(cred);
        eventPublisher.publish("CREDENTIAL_CREATED", null, null, "Created " + roleStr + " key for " + allowedBuckets);
        return ResponseEntity.ok(cred);
    }

    @DeleteMapping("/credentials/{accessKey}")
    public ResponseEntity<Void> deleteCredential(@PathVariable("accessKey") String accessKey) {
        credentialRepository.deleteCredential(accessKey);
        eventPublisher.publish("CREDENTIAL_DELETED", null, null, "Revoked API key: " + accessKey);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/buckets")
    public ResponseEntity<List<Bucket>> listBuckets() {
        return ResponseEntity.ok(bucketService.listBuckets());
    }

    @PostMapping("/buckets")
    public ResponseEntity<Bucket> createBucket(@RequestParam("name") String name) {
        Bucket bucket = bucketService.createBucket(name);
        eventPublisher.publish("BUCKET_CREATED", name, null, "Created bucket: " + name);
        return ResponseEntity.ok(bucket);
    }

    @PutMapping("/buckets/{bucket}/versioning")
    public ResponseEntity<Void> setBucketVersioning(
            @PathVariable("bucket") String bucket,
            @RequestParam("status") String status) {
        bucketService.setVersioning(bucket, VersioningStatus.fromString(status));
        eventPublisher.publish("VERSIONING_CHANGED", bucket, null, "Versioning updated to: " + status);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/buckets/{bucket}")
    public ResponseEntity<Void> deleteBucket(@PathVariable("bucket") String bucket) {
        bucketService.deleteBucket(bucket);
        eventPublisher.publish("BUCKET_DELETED", bucket, null, "Deleted bucket: " + bucket);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/buckets/{bucket}/objects")
    public ResponseEntity<List<S3Object>> listObjects(
            @PathVariable("bucket") String bucket,
            @RequestParam(value = "prefix", required = false, defaultValue = "") String prefix) {
        return ResponseEntity.ok(objectService.listObjects(bucket, prefix, null, 1000));
    }

    @GetMapping("/buckets/{bucket}/versions")
    public ResponseEntity<List<S3Object>> listObjectVersions(
            @PathVariable("bucket") String bucket,
            @RequestParam(value = "prefix", required = false, defaultValue = "") String prefix) {
        return ResponseEntity.ok(objectService.listObjectVersions(bucket, prefix, null, null, 1000));
    }

    @DeleteMapping("/buckets/{bucket}/objects")
    public ResponseEntity<Void> deleteObject(
            @PathVariable("bucket") String bucket,
            @RequestParam("key") String key,
            @RequestParam(value = "versionId", required = false) String versionId) {
        objectService.deleteObject(bucket, key, versionId);
        return ResponseEntity.noContent().build();
    }
}
