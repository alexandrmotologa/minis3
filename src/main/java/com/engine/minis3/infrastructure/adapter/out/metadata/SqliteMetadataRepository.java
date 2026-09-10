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
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * SQLite implementation of metadata repository ports with WAL mode,
 * connection pooling, object versioning, and batch operations.
 */
@Repository
public class SqliteMetadataRepository implements BucketRepositoryPort, ObjectMetadataPort, MultipartMetadataPort, com.engine.minis3.domain.port.CredentialRepositoryPort {

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

                // Execute schema.sql
                ClassPathResource resource = new ClassPathResource("schema.sql");
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    String ddl = reader.lines().collect(Collectors.joining("\n"));
                    for (String sql : ddl.split(";")) {
                        if (!sql.trim().isEmpty()) {
                            stmt.execute(sql.trim());
                        }
                    }
                }

                // Automatic schema evolution / migration for existing databases
                applyMigrations(conn);
            }
            log.info("Initialized SQLite metadata store at: {}", dbPath);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize SQLite metadata database", e);
        }
    }

    private void applyMigrations(Connection conn) {
        // Ensure buckets.versioning_status exists
        try (Statement s = conn.createStatement()) {
            s.execute("ALTER TABLE buckets ADD COLUMN versioning_status TEXT NOT NULL DEFAULT 'Off'");
        } catch (SQLException ignored) {
            // Column already exists
        }

        // Ensure objects.version_id, is_latest, is_delete_marker exist
        try (Statement s = conn.createStatement()) {
            s.execute("ALTER TABLE objects ADD COLUMN version_id TEXT NOT NULL DEFAULT 'null'");
        } catch (SQLException ignored) {}
        try (Statement s = conn.createStatement()) {
            s.execute("ALTER TABLE objects ADD COLUMN is_latest INTEGER NOT NULL DEFAULT 1");
        } catch (SQLException ignored) {}
        try (Statement s = conn.createStatement()) {
            s.execute("ALTER TABLE objects ADD COLUMN is_delete_marker INTEGER NOT NULL DEFAULT 0");
        } catch (SQLException ignored) {}

        // Ensure object_chunks.version_id exists
        try (Statement s = conn.createStatement()) {
            s.execute("ALTER TABLE object_chunks ADD COLUMN version_id TEXT NOT NULL DEFAULT 'null'");
        } catch (SQLException ignored) {}
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
        String sql = "INSERT INTO buckets (name, created_at, versioning_status) VALUES (?, ?, ?) " +
                "ON CONFLICT(name) DO UPDATE SET versioning_status = excluded.versioning_status";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bucket.getName());
            ps.setString(2, bucket.getCreatedAt().toString());
            ps.setString(3, bucket.getVersioningStatus().getValue());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save bucket: " + bucket.getName(), e);
        }
    }

    @Override
    public Optional<Bucket> findByName(String name) {
        String sql = "SELECT name, created_at, versioning_status FROM buckets WHERE name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String vStatus = rs.getString("versioning_status");
                    return Optional.of(new Bucket(
                            rs.getString("name"),
                            Instant.parse(rs.getString("created_at")),
                            VersioningStatus.fromString(vStatus)
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
        String sql = "SELECT name, created_at, versioning_status FROM buckets ORDER BY name ASC";
        List<Bucket> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String vStatus = rs.getString("versioning_status");
                result.add(new Bucket(
                        rs.getString("name"),
                        Instant.parse(rs.getString("created_at")),
                        VersioningStatus.fromString(vStatus)
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

    @Override
    public void updateVersioningStatus(String name, VersioningStatus status) {
        String sql = "UPDATE buckets SET versioning_status = ? WHERE name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.getValue());
            ps.setString(2, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update bucket versioning status: " + name, e);
        }
    }

    // ==================== ObjectMetadataPort ====================

    @Override
    public void save(S3Object object) {
        String markPreviousOldSql = "UPDATE objects SET is_latest = 0 WHERE bucket_name = ? AND key = ?";
        String deleteUnversionedObjSql = "DELETE FROM objects WHERE bucket_name = ? AND key = ? AND version_id = 'null'";
        String insertObjSql = "INSERT INTO objects (bucket_name, key, version_id, is_latest, is_delete_marker, size, etag, content_type, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String insertChunkSql = "INSERT INTO object_chunks (bucket_name, object_key, version_id, chunk_order, chunk_hash, chunk_offset, chunk_length) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if ("null".equalsIgnoreCase(object.getVersionId())) {
                    // Unversioned: delete prior null version to overwrite cleanly
                    try (PreparedStatement delPs = conn.prepareStatement(deleteUnversionedObjSql)) {
                        delPs.setString(1, object.getBucketName());
                        delPs.setString(2, object.getKey());
                        delPs.executeUpdate();
                    }
                } else {
                    // Versioned: mark previous entries as non-latest
                    try (PreparedStatement markPs = conn.prepareStatement(markPreviousOldSql)) {
                        markPs.setString(1, object.getBucketName());
                        markPs.setString(2, object.getKey());
                        markPs.executeUpdate();
                    }
                }

                try (PreparedStatement objPs = conn.prepareStatement(insertObjSql)) {
                    objPs.setString(1, object.getBucketName());
                    objPs.setString(2, object.getKey());
                    objPs.setString(3, object.getVersionId());
                    objPs.setInt(4, object.isLatest() ? 1 : 0);
                    objPs.setInt(5, object.isDeleteMarker() ? 1 : 0);
                    objPs.setLong(6, object.getSize());
                    objPs.setString(7, object.getEtag());
                    objPs.setString(8, object.getContentType());
                    objPs.setString(9, object.getCreatedAt().toString());
                    objPs.executeUpdate();
                }

                if (!object.isDeleteMarker() && !object.getChunks().isEmpty()) {
                    try (PreparedStatement chunkPs = conn.prepareStatement(insertChunkSql)) {
                        for (ObjectChunkRef ref : object.getChunks()) {
                            chunkPs.setString(1, object.getBucketName());
                            chunkPs.setString(2, object.getKey());
                            chunkPs.setString(3, object.getVersionId());
                            chunkPs.setInt(4, ref.getOrder());
                            chunkPs.setString(5, ref.getHash().getValue());
                            chunkPs.setLong(6, ref.getOffset());
                            chunkPs.setLong(7, ref.getLength());
                            chunkPs.addBatch();
                        }
                        chunkPs.executeBatch();
                    }
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
        String objSql = "SELECT bucket_name, key, version_id, is_latest, is_delete_marker, size, etag, content_type, created_at " +
                "FROM objects WHERE bucket_name = ? AND key = ? AND is_latest = 1";
        return fetchObjectByQuery(objSql, bucketName, key, null);
    }

    @Override
    public Optional<S3Object> findByBucketAndKeyAndVersion(String bucketName, String key, String versionId) {
        if (versionId == null || versionId.isBlank() || "null".equalsIgnoreCase(versionId)) {
            return findByBucketAndKey(bucketName, key);
        }
        String objSql = "SELECT bucket_name, key, version_id, is_latest, is_delete_marker, size, etag, content_type, created_at " +
                "FROM objects WHERE bucket_name = ? AND key = ? AND version_id = ?";
        return fetchObjectByQuery(objSql, bucketName, key, versionId);
    }

    private Optional<S3Object> fetchObjectByQuery(String objSql, String bucketName, String key, String versionId) {
        String chunksSql = "SELECT chunk_order, chunk_hash, chunk_offset, chunk_length FROM object_chunks " +
                "WHERE bucket_name = ? AND object_key = ? AND version_id = ? ORDER BY chunk_order ASC";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(objSql)) {
            ps.setString(1, bucketName);
            ps.setString(2, key);
            if (versionId != null) {
                ps.setString(3, versionId);
            }

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String actualVersionId = rs.getString("version_id");
                    boolean isLatest = rs.getInt("is_latest") == 1;
                    boolean isDeleteMarker = rs.getInt("is_delete_marker") == 1;
                    long size = rs.getLong("size");
                    String etag = rs.getString("etag");
                    String contentType = rs.getString("content_type");
                    Instant createdAt = Instant.parse(rs.getString("created_at"));

                    List<ObjectChunkRef> chunkRefs = new ArrayList<>();
                    if (!isDeleteMarker) {
                        try (PreparedStatement chunkPs = conn.prepareStatement(chunksSql)) {
                            chunkPs.setString(1, bucketName);
                            chunkPs.setString(2, key);
                            chunkPs.setString(3, actualVersionId);
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
                    }

                    return Optional.of(new S3Object(
                            bucketName, key, actualVersionId, isLatest, isDeleteMarker,
                            size, etag, contentType, createdAt, chunkRefs
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find object: " + bucketName + "/" + key, e);
        }
        return Optional.empty();
    }

    @Override
    public void delete(String bucketName, String key) {
        // Find bucket versioning status
        Optional<Bucket> bucketOpt = findByName(bucketName);
        VersioningStatus status = bucketOpt.map(Bucket::getVersioningStatus).orElse(VersioningStatus.OFF);

        if (status == VersioningStatus.ENABLED) {
            // Create a Delete Marker as the latest version
            String newVersionId = UUID.randomUUID().toString();
            S3Object marker = S3Object.deleteMarker(bucketName, key, newVersionId, Instant.now());
            save(marker);
        } else {
            // Hard delete latest version or unversioned object
            Optional<S3Object> objOpt = findByBucketAndKey(bucketName, key);
            if (objOpt.isPresent()) {
                deleteVersion(bucketName, key, objOpt.get().getVersionId());
            }
        }
    }

    @Override
    public void deleteVersion(String bucketName, String key, String versionId) {
        String findChunksSql = "SELECT chunk_hash FROM object_chunks WHERE bucket_name = ? AND object_key = ? AND version_id = ?";
        String deleteChunksRefSql = "DELETE FROM object_chunks WHERE bucket_name = ? AND object_key = ? AND version_id = ?";
        String deleteObjSql = "DELETE FROM objects WHERE bucket_name = ? AND key = ? AND version_id = ?";
        String decSql = "UPDATE chunks SET ref_count = max(0, ref_count - 1) WHERE hash = ?";
        String promoteLatestSql = "UPDATE objects SET is_latest = 1 WHERE rowid = (SELECT rowid FROM objects WHERE bucket_name = ? AND key = ? ORDER BY created_at DESC LIMIT 1)";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                List<ChunkHash> hashes = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(findChunksSql)) {
                    ps.setString(1, bucketName);
                    ps.setString(2, key);
                    ps.setString(3, versionId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            hashes.add(new ChunkHash(rs.getString("chunk_hash")));
                        }
                    }
                }

                try (PreparedStatement delChunksPs = conn.prepareStatement(deleteChunksRefSql)) {
                    delChunksPs.setString(1, bucketName);
                    delChunksPs.setString(2, key);
                    delChunksPs.setString(3, versionId);
                    delChunksPs.executeUpdate();
                }

                try (PreparedStatement delPs = conn.prepareStatement(deleteObjSql)) {
                    delPs.setString(1, bucketName);
                    delPs.setString(2, key);
                    delPs.setString(3, versionId);
                    delPs.executeUpdate();
                }

                if (!hashes.isEmpty()) {
                    try (PreparedStatement decPs = conn.prepareStatement(decSql)) {
                        for (ChunkHash hash : hashes) {
                            decPs.setString(1, hash.getValue());
                            decPs.addBatch();
                        }
                        decPs.executeBatch();
                    }
                }

                // Promote the most recent remaining version if this key still has versions
                try (PreparedStatement promPs = conn.prepareStatement(promoteLatestSql)) {
                    promPs.setString(1, bucketName);
                    promPs.setString(2, key);
                    promPs.executeUpdate();
                }

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete object version: " + bucketName + "/" + key + "?versionId=" + versionId, e);
        }
    }

    @Override
    public void deleteBatch(String bucketName, List<ObjectKeyVersion> targets) {
        if (targets == null || targets.isEmpty()) {
            return;
        }

        Optional<Bucket> bucketOpt = findByName(bucketName);
        VersioningStatus status = bucketOpt.map(Bucket::getVersioningStatus).orElse(VersioningStatus.OFF);

        for (ObjectKeyVersion target : targets) {
            if (target.hasVersion()) {
                deleteVersion(bucketName, target.key(), target.versionId());
            } else {
                if (status == VersioningStatus.ENABLED) {
                    String markerVersionId = UUID.randomUUID().toString();
                    save(S3Object.deleteMarker(bucketName, target.key(), markerVersionId, Instant.now()));
                } else {
                    Optional<S3Object> current = findByBucketAndKey(bucketName, target.key());
                    current.ifPresent(s3Object -> deleteVersion(bucketName, target.key(), s3Object.getVersionId()));
                }
            }
        }
    }

    @Override
    public List<S3Object> listObjects(String bucketName, String prefix, String continuationToken, int maxKeys) {
        StringBuilder sql = new StringBuilder("SELECT bucket_name, key, version_id, is_latest, is_delete_marker, size, etag, content_type, created_at " +
                "FROM objects WHERE bucket_name = ? AND is_latest = 1 AND is_delete_marker = 0");
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
                            rs.getString("version_id"),
                            rs.getInt("is_latest") == 1,
                            rs.getInt("is_delete_marker") == 1,
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
    public List<S3Object> listObjectVersions(String bucketName, String prefix, String keyMarker, String versionIdMarker, int maxKeys) {
        StringBuilder sql = new StringBuilder("SELECT bucket_name, key, version_id, is_latest, is_delete_marker, size, etag, content_type, created_at " +
                "FROM objects WHERE bucket_name = ?");
        List<Object> params = new ArrayList<>();
        params.add(bucketName);

        if (prefix != null && !prefix.isBlank()) {
            sql.append(" AND key LIKE ?");
            params.add(prefix + "%");
        }

        if (keyMarker != null && !keyMarker.isBlank()) {
            sql.append(" AND key >= ?");
            params.add(keyMarker);
        }

        sql.append(" ORDER BY key ASC, created_at DESC LIMIT ?");
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
                            rs.getString("version_id"),
                            rs.getInt("is_latest") == 1,
                            rs.getInt("is_delete_marker") == 1,
                            rs.getLong("size"),
                            rs.getString("etag"),
                            rs.getString("content_type"),
                            Instant.parse(rs.getString("created_at")),
                            List.of()
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list object versions for bucket: " + bucketName, e);
        }
        return result;
    }

    @Override
    public long countByBucket(String bucketName) {
        String sql = "SELECT count(*) FROM objects WHERE bucket_name = ? AND is_latest = 1 AND is_delete_marker = 0";
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
    public List<ChunkHash> listAllChunkHashes() {
        String sql = "SELECT hash FROM chunks WHERE ref_count > 0 ORDER BY created_at ASC";
        List<ChunkHash> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new ChunkHash(rs.getString("hash")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list active chunk hashes", e);
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
        String countObjectsSql = "SELECT count(*), coalesce(sum(size), 0) FROM objects WHERE is_latest = 1 AND is_delete_marker = 0";
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

    // ==================== CredentialRepositoryPort ====================

    @Override
    public void saveCredential(ApiCredential credential) {
        String sql = "INSERT INTO api_credentials (access_key, secret_key, role, allowed_buckets, created_at) " +
                "VALUES (?, ?, ?, ?, ?) " +
                "ON CONFLICT(access_key) DO UPDATE SET " +
                "secret_key = excluded.secret_key, role = excluded.role, allowed_buckets = excluded.allowed_buckets";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, credential.getAccessKey());
            ps.setString(2, credential.getSecretKey());
            ps.setString(3, credential.getRole().name());
            ps.setString(4, credential.getAllowedBuckets());
            ps.setString(5, credential.getCreatedAt().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save API credential: " + credential.getAccessKey(), e);
        }
    }

    @Override
    public Optional<ApiCredential> findByAccessKey(String accessKey) {
        String sql = "SELECT access_key, secret_key, role, allowed_buckets, created_at FROM api_credentials WHERE access_key = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accessKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new ApiCredential(
                            rs.getString("access_key"),
                            rs.getString("secret_key"),
                            ApiCredential.Role.fromString(rs.getString("role")),
                            rs.getString("allowed_buckets"),
                            Instant.parse(rs.getString("created_at"))
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find API credential: " + accessKey, e);
        }
        return Optional.empty();
    }

    @Override
    public List<ApiCredential> listCredentials() {
        String sql = "SELECT access_key, secret_key, role, allowed_buckets, created_at FROM api_credentials ORDER BY created_at ASC";
        List<ApiCredential> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new ApiCredential(
                        rs.getString("access_key"),
                        rs.getString("secret_key"),
                        ApiCredential.Role.fromString(rs.getString("role")),
                        rs.getString("allowed_buckets"),
                        Instant.parse(rs.getString("created_at"))
                ));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list API credentials", e);
        }
        return result;
    }

    @Override
    public void deleteCredential(String accessKey) {
        String sql = "DELETE FROM api_credentials WHERE access_key = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accessKey);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete API credential: " + accessKey, e);
        }
    }
}
