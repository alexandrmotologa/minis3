package com.engine.minis3.infrastructure.adapter.out.metadata;

import com.engine.minis3.domain.model.*;
import com.engine.minis3.domain.port.BucketRepositoryPort;
import com.engine.minis3.domain.port.MultipartMetadataPort;
import com.engine.minis3.domain.port.ObjectMetadataPort;
import com.engine.minis3.infrastructure.config.MiniS3Properties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * SQLite implementation of metadata repository ports with WAL mode and connection pooling.
 */
@Repository
public class SqliteMetadataRepository implements BucketRepositoryPort, ObjectMetadataPort, MultipartMetadataPort {

    private static final Logger log = LoggerFactory.getLogger(SqliteMetadataRepository.class);

    private final Path dbPath;
    private HikariDataSource dataSource;

    public SqliteMetadataRepository(MiniS3Properties properties) {
        this.dbPath = properties.getStorage().getDatabasePath();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(dbPath.getParent());
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:sqlite:" + dbPath.toString().replace('\\', '/'));
            config.setMaximumPoolSize(5);
            config.setMinimumIdle(1);
            config.setPoolName("MiniS3-SQLite-Pool");
            config.addDataSourceProperty("journal_mode", "WAL");
            config.addDataSourceProperty("synchronous", "NORMAL");
            config.addDataSourceProperty("foreign_keys", "ON");
            config.addDataSourceProperty("busy_timeout", "10000");

            this.dataSource = new HikariDataSource(config);

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA journal_mode = WAL;");
                stmt.execute("PRAGMA synchronous = NORMAL;");
                stmt.execute("PRAGMA foreign_keys = ON;");

                // Run schema.sql
                ClassPathResource resource = new ClassPathResource("schema.sql");
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    String ddl = reader.lines().collect(Collectors.joining("\n"));
                    for (String sql : ddl.split(";")) {
                        if (!sql.trim().isEmpty()) {
                            stmt.execute(sql.trim());
                        }
                    }
                }
            }
            log.info("Initialized SQLite metadata store at: {}", dbPath);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize SQLite metadata database", e);
        }
    }

    @PreDestroy
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    // ==================== BucketRepositoryPort ====================

    @Override
    public void save(Bucket bucket) {
        String sql = "INSERT INTO buckets (name, created_at) VALUES (?, ?) ON CONFLICT(name) DO NOTHING";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bucket.getName());
            ps.setString(2, bucket.getCreatedAt().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save bucket: " + bucket.getName(), e);
        }
    }

    @Override
    public Optional<Bucket> findByName(String name) {
        String sql = "SELECT name, created_at FROM buckets WHERE name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Bucket(
                            rs.getString("name"),
                            Instant.parse(rs.getString("created_at"))
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query bucket: " + name, e);
        }
        return Optional.empty();
    }

    @Override
    public void delete(String name) {
        String sql = "DELETE FROM buckets WHERE name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete bucket: " + name, e);
        }
    }

    @Override
    public List<Bucket> listAll() {
        String sql = "SELECT name, created_at FROM buckets ORDER BY name ASC";
        List<Bucket> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new Bucket(
                        rs.getString("name"),
                        Instant.parse(rs.getString("created_at"))
                ));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list buckets", e);
        }
        return result;
    }

    @Override
    public boolean exists(String name) {
        String sql = "SELECT 1 FROM buckets WHERE name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to check bucket existence: " + name, e);
        }
    }

    // ==================== ObjectMetadataPort ====================

    @Override
    public void save(S3Object object) {
        String deleteSql = "DELETE FROM objects WHERE bucket_name = ? AND key = ?";
        String insertObjSql = "INSERT INTO objects (bucket_name, key, size, etag, content_type, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        String insertChunkSql = "INSERT INTO object_chunks (bucket_name, object_key, chunk_order, chunk_hash, chunk_offset, chunk_length) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Delete existing object entry if overwriting
                try (PreparedStatement delPs = conn.prepareStatement(deleteSql)) {
                    delPs.setString(1, object.getBucketName());
                    delPs.setString(2, object.getKey());
                    delPs.executeUpdate();
                }

                try (PreparedStatement objPs = conn.prepareStatement(insertObjSql)) {
                    objPs.setString(1, object.getBucketName());
                    objPs.setString(2, object.getKey());
                    objPs.setLong(3, object.getSize());
                    objPs.setString(4, object.getEtag());
                    objPs.setString(5, object.getContentType());
                    objPs.setString(6, object.getCreatedAt().toString());
                    objPs.executeUpdate();
                }

                try (PreparedStatement chunkPs = conn.prepareStatement(insertChunkSql)) {
                    for (ObjectChunkRef ref : object.getChunks()) {
                        chunkPs.setString(1, object.getBucketName());
                        chunkPs.setString(2, object.getKey());
                        chunkPs.setInt(3, ref.getOrder());
                        chunkPs.setString(4, ref.getHash().getValue());
                        chunkPs.setLong(5, ref.getOffset());
                        chunkPs.setLong(6, ref.getLength());
                        chunkPs.addBatch();
                    }
                    chunkPs.executeBatch();
                }

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save S3Object: " + object.getKey(), e);
        }
    }

    @Override
    public Optional<S3Object> findByBucketAndKey(String bucketName, String key) {
        String objSql = "SELECT bucket_name, key, size, etag, content_type, created_at FROM objects WHERE bucket_name = ? AND key = ?";
        String chunksSql = "SELECT chunk_order, chunk_hash, chunk_offset, chunk_length FROM object_chunks WHERE bucket_name = ? AND object_key = ? ORDER BY chunk_order ASC";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(objSql)) {
            ps.setString(1, bucketName);
            ps.setString(2, key);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long size = rs.getLong("size");
                    String etag = rs.getString("etag");
                    String contentType = rs.getString("content_type");
                    Instant createdAt = Instant.parse(rs.getString("created_at"));

                    List<ObjectChunkRef> chunkRefs = new ArrayList<>();
                    try (PreparedStatement chunkPs = conn.prepareStatement(chunksSql)) {
                        chunkPs.setString(1, bucketName);
                        chunkPs.setString(2, key);
                        try (ResultSet chunkRs = chunkPs.executeQuery()) {
                            while (chunkRs.next()) {
                                chunkRefs.add(new ObjectChunkRef(
                                        chunkRs.getInt("chunk_order"),
                                        new ChunkHash(chunkRs.getString("chunk_hash")),
                                        chunkRs.getLong("chunk_offset"),
                                        chunkRs.getLong("chunk_length")
                                ));
                            }
                        }
                    }

                    return Optional.of(new S3Object(bucketName, key, size, etag, contentType, createdAt, chunkRefs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find object: " + bucketName + "/" + key, e);
        }
        return Optional.empty();
    }

    @Override
    public void delete(String bucketName, String key) {
        String findChunksSql = "SELECT chunk_hash FROM object_chunks WHERE bucket_name = ? AND object_key = ?";
        String deleteObjSql = "DELETE FROM objects WHERE bucket_name = ? AND key = ?";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                List<ChunkHash> hashes = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(findChunksSql)) {
                    ps.setString(1, bucketName);
                    ps.setString(2, key);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            hashes.add(new ChunkHash(rs.getString("chunk_hash")));
                        }
                    }
                }

                try (PreparedStatement delPs = conn.prepareStatement(deleteObjSql)) {
                    delPs.setString(1, bucketName);
                    delPs.setString(2, key);
                    delPs.executeUpdate();
                }

                String decSql = "UPDATE chunks SET ref_count = max(0, ref_count - 1) WHERE hash = ?";
                try (PreparedStatement decPs = conn.prepareStatement(decSql)) {
                    for (ChunkHash hash : hashes) {
                        decPs.setString(1, hash.getValue());
                        decPs.addBatch();
                    }
                    decPs.executeBatch();
                }

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete object: " + bucketName + "/" + key, e);
        }
    }

    @Override
    public List<S3Object> listObjects(String bucketName, String prefix, String continuationToken, int maxKeys) {
        StringBuilder sql = new StringBuilder("SELECT bucket_name, key, size, etag, content_type, created_at FROM objects WHERE bucket_name = ?");
        List<Object> params = new ArrayList<>();
        params.add(bucketName);

        if (prefix != null && !prefix.isBlank()) {
            sql.append(" AND key LIKE ?");
            params.add(prefix + "%");
        }

        if (continuationToken != null && !continuationToken.isBlank()) {
            sql.append(" AND key > ?");
            params.add(continuationToken);
        }

        sql.append(" ORDER BY key ASC LIMIT ?");
        params.add(Math.max(1, Math.min(maxKeys, 1000)));

        List<S3Object> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new S3Object(
                            rs.getString("bucket_name"),
                            rs.getString("key"),
                            rs.getLong("size"),
                            rs.getString("etag"),
                            rs.getString("content_type"),
                            Instant.parse(rs.getString("created_at")),
                            List.of()
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list objects in bucket: " + bucketName, e);
        }
        return result;
    }

    @Override
    public long countByBucket(String bucketName) {
        String sql = "SELECT count(*) FROM objects WHERE bucket_name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bucketName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to count objects in bucket: " + bucketName, e);
        }
        return 0;
    }

    @Override
    public void recordChunk(Chunk chunk) {
        String sql = "INSERT INTO chunks (hash, raw_size, compressed_size, ref_count, created_at) VALUES (?, ?, ?, ?, ?) " +
                "ON CONFLICT(hash) DO UPDATE SET ref_count = ref_count + 1";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, chunk.getHash().getValue());
            ps.setLong(2, chunk.getRawSize());
            ps.setLong(3, chunk.getCompressedSize());
            ps.setLong(4, chunk.getRefCount());
            ps.setString(5, chunk.getCreatedAt().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to record chunk: " + chunk.getHash(), e);
        }
    }

    @Override
    public Optional<Chunk> findChunk(ChunkHash hash) {
        String sql = "SELECT hash, raw_size, compressed_size, ref_count, created_at FROM chunks WHERE hash = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hash.getValue());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Chunk(
                            new ChunkHash(rs.getString("hash")),
                            rs.getLong("raw_size"),
                            rs.getLong("compressed_size"),
                            rs.getLong("ref_count"),
                            Instant.parse(rs.getString("created_at"))
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find chunk: " + hash, e);
        }
        return Optional.empty();
    }

    @Override
    public void incrementChunkRef(ChunkHash hash) {
        String sql = "UPDATE chunks SET ref_count = ref_count + 1 WHERE hash = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hash.getValue());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to increment chunk ref: " + hash, e);
        }
    }

    @Override
    public void decrementChunkRef(ChunkHash hash) {
        String sql = "UPDATE chunks SET ref_count = max(0, ref_count - 1) WHERE hash = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hash.getValue());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to decrement chunk ref: " + hash, e);
        }
    }

    @Override
    public List<ChunkHash> findOrphanChunks() {
        String sql = "SELECT hash FROM chunks WHERE ref_count <= 0";
        List<ChunkHash> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new ChunkHash(rs.getString("hash")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query orphan chunks", e);
        }
        return result;
    }

    @Override
    public void deleteChunk(ChunkHash hash) {
        String sql = "DELETE FROM chunks WHERE hash = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hash.getValue());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete chunk metadata: " + hash, e);
        }
    }

    @Override
    public StorageStats getStorageStats() {
        String countBucketsSql = "SELECT count(*) FROM buckets";
        String countObjectsSql = "SELECT count(*), coalesce(sum(size), 0) FROM objects";
        String countChunksSql = "SELECT count(*), coalesce(sum(compressed_size), 0) FROM chunks WHERE ref_count > 0";

        long totalBuckets = 0;
        long totalObjects = 0;
        long totalRawBytes = 0;
        long totalChunks = 0;
        long totalCompressedBytes = 0;

        try (Connection conn = dataSource.getConnection()) {
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery(countBucketsSql)) {
                if (rs.next()) totalBuckets = rs.getLong(1);
            }

            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery(countObjectsSql)) {
                if (rs.next()) {
                    totalObjects = rs.getLong(1);
                    totalRawBytes = rs.getLong(2);
                }
            }

            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery(countChunksSql)) {
                if (rs.next()) {
                    totalChunks = rs.getLong(1);
                    totalCompressedBytes = rs.getLong(2);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to calculate storage stats", e);
        }

        return new StorageStats(totalBuckets, totalObjects, totalRawBytes, totalCompressedBytes, totalChunks);
    }

    // ==================== MultipartMetadataPort ====================

    @Override
    public void saveUpload(MultipartUpload upload) {
        String sql = "INSERT INTO multipart_uploads (upload_id, bucket_name, object_key, content_type, initiated_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, upload.getUploadId());
            ps.setString(2, upload.getBucketName());
            ps.setString(3, upload.getKey());
            ps.setString(4, upload.getContentType());
            ps.setString(5, upload.getInitiatedAt().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save multipart upload: " + upload.getUploadId(), e);
        }
    }

    @Override
    public Optional<MultipartUpload> findUpload(String uploadId) {
        String sql = "SELECT upload_id, bucket_name, object_key, content_type, initiated_at FROM multipart_uploads WHERE upload_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uploadId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new MultipartUpload(
                            rs.getString("upload_id"),
                            rs.getString("bucket_name"),
                            rs.getString("object_key"),
                            rs.getString("content_type"),
                            Instant.parse(rs.getString("initiated_at"))
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find upload: " + uploadId, e);
        }
        return Optional.empty();
    }

    @Override
    public void deleteUpload(String uploadId) {
        String sql = "DELETE FROM multipart_uploads WHERE upload_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uploadId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete upload: " + uploadId, e);
        }
    }

    @Override
    public void savePart(MultipartPart part) {
        String sql = "INSERT INTO multipart_parts (upload_id, part_number, etag, size, chunk_hash, uploaded_at) VALUES (?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(upload_id, part_number) DO UPDATE SET etag = excluded.etag, size = excluded.size, chunk_hash = excluded.chunk_hash, uploaded_at = excluded.uploaded_at";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, part.getUploadId());
            ps.setInt(2, part.getPartNumber());
            ps.setString(3, part.getEtag());
            ps.setLong(4, part.getSize());
            ps.setString(5, part.getChunkHash().getValue());
            ps.setString(6, part.getUploadedAt().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save multipart part: " + part.getUploadId() + " #" + part.getPartNumber(), e);
        }
    }

    @Override
    public List<MultipartPart> listParts(String uploadId) {
        String sql = "SELECT upload_id, part_number, etag, size, chunk_hash, uploaded_at FROM multipart_parts WHERE upload_id = ? ORDER BY part_number ASC";
        List<MultipartPart> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uploadId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new MultipartPart(
                            rs.getString("upload_id"),
                            rs.getInt("part_number"),
                            rs.getString("etag"),
                            rs.getLong("size"),
                            new ChunkHash(rs.getString("chunk_hash")),
                            Instant.parse(rs.getString("uploaded_at"))
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list parts for upload: " + uploadId, e);
        }
        return result;
    }

    @Override
    public Optional<MultipartPart> findPart(String uploadId, int partNumber) {
        String sql = "SELECT upload_id, part_number, etag, size, chunk_hash, uploaded_at FROM multipart_parts WHERE upload_id = ? AND part_number = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uploadId);
            ps.setInt(2, partNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new MultipartPart(
                            rs.getString("upload_id"),
                            rs.getInt("part_number"),
                            rs.getString("etag"),
                            rs.getLong("size"),
                            new ChunkHash(rs.getString("chunk_hash")),
                            Instant.parse(rs.getString("uploaded_at"))
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find part: " + uploadId + " #" + partNumber, e);
        }
        return Optional.empty();
    }
}
