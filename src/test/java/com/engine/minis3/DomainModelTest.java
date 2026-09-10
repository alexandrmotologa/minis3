package com.engine.minis3;

import com.engine.minis3.domain.exception.InvalidBucketNameException;
import com.engine.minis3.domain.model.Bucket;
import com.engine.minis3.domain.model.ChunkHash;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class DomainModelTest {

    @Test
    void testChunkHashValidAndSharding() {
        String hashStr = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        ChunkHash hash = new ChunkHash(hashStr);

        assertEquals(hashStr, hash.getValue());
        assertEquals("e3/b0", hash.getShardDirectory());
        assertEquals("e3/b0/" + hashStr + ".zstd", hash.getShardPath());
    }

    @Test
    void testChunkHashInvalidThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ChunkHash("not-a-hash"));
        assertThrows(IllegalArgumentException.class, () -> new ChunkHash(null));
    }

    @Test
    void testBucketValidation() {
        // Valid bucket names
        Bucket b1 = new Bucket("my-valid-bucket-123", Instant.now());
        assertEquals("my-valid-bucket-123", b1.getName());

        // Invalid bucket names
        assertThrows(InvalidBucketNameException.class, () -> new Bucket("ab", Instant.now())); // too short
        assertThrows(InvalidBucketNameException.class, () -> new Bucket("-invalid", Instant.now())); // starts with dash
        assertThrows(InvalidBucketNameException.class, () -> new Bucket("Invalid_Name", Instant.now())); // uppercase / underscore
        assertThrows(InvalidBucketNameException.class, () -> new Bucket("bucket..name", Instant.now())); // consecutive dots
    }
}
