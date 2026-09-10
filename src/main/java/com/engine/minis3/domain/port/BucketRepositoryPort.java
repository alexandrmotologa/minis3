package com.engine.minis3.domain.port;

import com.engine.minis3.domain.model.Bucket;
import com.engine.minis3.domain.model.VersioningStatus;

import java.util.List;
import java.util.Optional;

public interface BucketRepositoryPort {
    void save(Bucket bucket);
    Optional<Bucket> findByName(String name);
    void delete(String name);
    List<Bucket> listAll();
    boolean exists(String name);
    void updateVersioningStatus(String name, VersioningStatus status);
}
