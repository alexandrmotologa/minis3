package com.engine.minis3.application.service;

import com.engine.minis3.domain.exception.BucketAlreadyExistsException;
import com.engine.minis3.domain.exception.BucketNotEmptyException;
import com.engine.minis3.domain.exception.NoSuchBucketException;
import com.engine.minis3.domain.model.Bucket;
import com.engine.minis3.domain.model.VersioningStatus;
import com.engine.minis3.domain.port.BucketRepositoryPort;
import com.engine.minis3.domain.port.ObjectMetadataPort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class BucketService {

    private final BucketRepositoryPort bucketRepository;
    private final ObjectMetadataPort objectMetadata;

    public BucketService(BucketRepositoryPort bucketRepository, ObjectMetadataPort objectMetadata) {
        this.bucketRepository = bucketRepository;
        this.objectMetadata = objectMetadata;
    }

    public Bucket createBucket(String bucketName) {
        Bucket.validateName(bucketName);
        if (bucketRepository.exists(bucketName)) {
            throw new BucketAlreadyExistsException(bucketName);
        }
        Bucket bucket = new Bucket(bucketName, Instant.now());
        bucketRepository.save(bucket);
        return bucket;
    }

    public Bucket getBucket(String bucketName) {
        return bucketRepository.findByName(bucketName)
                .orElseThrow(() -> new NoSuchBucketException(bucketName));
    }

    public List<Bucket> listBuckets() {
        return bucketRepository.listAll();
    }

    public void deleteBucket(String bucketName) {
        if (!bucketRepository.exists(bucketName)) {
            throw new NoSuchBucketException(bucketName);
        }
        long objectCount = objectMetadata.countByBucket(bucketName);
        if (objectCount > 0) {
            throw new BucketNotEmptyException(bucketName);
        }
        bucketRepository.delete(bucketName);
    }

    public void setVersioning(String bucketName, VersioningStatus status) {
        if (!bucketRepository.exists(bucketName)) {
            throw new NoSuchBucketException(bucketName);
        }
        bucketRepository.updateVersioningStatus(bucketName, status);
    }

    public VersioningStatus getVersioning(String bucketName) {
        return getBucket(bucketName).getVersioningStatus();
    }
}
