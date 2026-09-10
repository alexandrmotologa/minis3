package com.engine.minis3.infrastructure.adapter.out.storage;

import com.engine.minis3.domain.model.ChunkHash;
import com.engine.minis3.domain.port.ChunkStorePort;
import com.engine.minis3.infrastructure.config.MiniS3Properties;
import com.github.luben.zstd.Zstd;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Filesystem implementation of Content-Addressable Storage (CAS) with Zstandard compression.
 */
@Component
public class ContentAddressableChunkStore implements ChunkStorePort {

    private static final Logger log = LoggerFactory.getLogger(ContentAddressableChunkStore.class);

    private final Path rootDir;
    private final Path chunksDir;
    private final Path tmpDir;

    public ContentAddressableChunkStore(MiniS3Properties properties) {
        this.rootDir = properties.getStorage().getRootPath();
        this.chunksDir = properties.getStorage().getChunksPath();
        this.tmpDir = properties.getStorage().getTmpPath();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(chunksDir);
            Files.createDirectories(tmpDir);
            cleanTmpDirectory();
            log.info("Initialized CAS chunk store at: {}", chunksDir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize CAS chunk storage directories", e);
        }
    }

    private void cleanTmpDirectory() {
        try (Stream<Path> stream = Files.walk(tmpDir)) {
            stream.filter(Files::isRegularFile).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {}
            });
        } catch (Exception e) {
            log.warn("Could not clean temporary directory: {}", e.getMessage());
        }
    }

    @Override
    public long writeChunk(ChunkHash hash, byte[] uncompressedData, int zstdLevel) {
        Path targetPath = getPathForHash(hash);
        if (Files.exists(targetPath)) {
            // Already persisted in CAS
            try {
                return Files.size(targetPath);
            } catch (IOException e) {
                return uncompressedData.length;
            }
        }

        try {
            Files.createDirectories(targetPath.getParent());
            byte[] compressed = (uncompressedData.length == 0)
                    ? new byte[0]
                    : Zstd.compress(uncompressedData, zstdLevel);

            Path tempFile = tmpDir.resolve(UUID.randomUUID() + ".tmp");
            Files.write(tempFile, compressed, StandardOpenOption.CREATE, StandardOpenOption.WRITE);

            try {
                Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            return compressed.length;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write CAS chunk: " + hash, e);
        }
    }

    @Override
    public byte[] readChunk(ChunkHash hash) {
        Path path = getPathForHash(hash);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("CAS chunk not found on disk: " + hash);
        }

        try {
            byte[] compressed = Files.readAllBytes(path);
            if (compressed.length == 0) {
                return new byte[0];
            }
            long originalSize = Zstd.decompressedSize(compressed);
            if (originalSize < 0) {
                throw new IllegalStateException("Corrupted Zstd frame for chunk: " + hash);
            }
            return Zstd.decompress(compressed, (int) originalSize);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read CAS chunk: " + hash, e);
        }
    }

    @Override
    public void deleteChunk(ChunkHash hash) {
        Path path = getPathForHash(hash);
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete chunk file: {}", path, e);
        }
    }

    @Override
    public boolean exists(ChunkHash hash) {
        return Files.exists(getPathForHash(hash));
    }

    public Path getPathForHash(ChunkHash hash) {
        return chunksDir.resolve(hash.getShardPath());
    }
}
