package com.engine.minis3.infrastructure.web;

import com.engine.minis3.application.service.AdminService;
import com.engine.minis3.application.service.BucketService;
import com.engine.minis3.application.service.ObjectService;
import com.engine.minis3.domain.model.Bucket;
import com.engine.minis3.domain.model.S3Object;
import com.engine.minis3.domain.model.StorageStats;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminApiController {

    private final AdminService adminService;
    private final BucketService bucketService;
    private final ObjectService objectService;

    public AdminApiController(AdminService adminService,
                              BucketService bucketService,
                              ObjectService objectService) {
        this.adminService = adminService;
        this.bucketService = bucketService;
        this.objectService = objectService;
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
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/buckets")
    public ResponseEntity<List<Bucket>> listBuckets() {
        return ResponseEntity.ok(bucketService.listBuckets());
    }

    @PostMapping("/buckets")
    public ResponseEntity<Bucket> createBucket(@RequestParam("name") String name) {
        return ResponseEntity.ok(bucketService.createBucket(name));
    }

    @DeleteMapping("/buckets/{bucket}")
    public ResponseEntity<Void> deleteBucket(@PathVariable("bucket") String bucket) {
        bucketService.deleteBucket(bucket);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/buckets/{bucket}/objects")
    public ResponseEntity<List<S3Object>> listObjects(
            @PathVariable("bucket") String bucket,
            @RequestParam(value = "prefix", required = false, defaultValue = "") String prefix) {
        return ResponseEntity.ok(objectService.listObjects(bucket, prefix, null, 1000));
    }

    @DeleteMapping("/buckets/{bucket}/objects")
    public ResponseEntity<Void> deleteObject(
            @PathVariable("bucket") String bucket,
            @RequestParam("key") String key) {
        objectService.deleteObject(bucket, key);
        return ResponseEntity.noContent().build();
    }
}
