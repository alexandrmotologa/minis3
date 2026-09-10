package com.engine.minis3.infrastructure.adapter.out.storage;

import com.engine.minis3.domain.model.ChunkHash;
import com.engine.minis3.domain.port.ChunkStorePort;
import com.engine.minis3.infrastructure.config.MiniS3Properties;
import com.github.luben.zstd.Zstd;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Filesystem implementation of Content-Addressable Storage (CAS) with adaptive Zstandard
 * compression and intelligent bypass for incompressible data.
 */
@Component
public class ContentAddressableChunkStore implements ChunkStorePort {

    private static final Logger log = LoggerFactory.getLogger(ContentAddressableChunkStore.class);

    private static final byte MODE_RAW = 0x00;
    private static final byte MODE_ZSTD = 0x01;
    private static final byte ZSTD_MAGIC_BYTE_0 = 0x28;
    private static final byte ZSTD_MAGIC_BYTE_1 = (byte) 0xB5;

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
            try {
                return Files.size(targetPath);
            } catch (IOException e) {
                return uncompressedData.length;
            }
        }

        try {
            Files.createDirectories(targetPath.getParent());

            byte[] filePayload;
            if (uncompressedData.length == 0) {
                filePayload = new byte[]{MODE_RAW};
            } else {
                double entropy = calculateEntropy(uncompressedData);
                // If entropy > 7.6, data is already compressed or high entropy
                if (entropy > 7.6) {
                    filePayload = new byte[uncompressedData.length + 1];
                    filePayload[0] = MODE_RAW;
                    System.arraycopy(uncompressedData, 0, filePayload, 1, uncompressedData.length);
                } else {
                    byte[] compressed = Zstd.compress(uncompressedData, zstdLevel);
                    if (compressed.length < uncompressedData.length) {
                        filePayload = new byte[compressed.length + 1];
                        filePayload[0] = MODE_ZSTD;
                        System.arraycopy(compressed, 0, filePayload, 1, compressed.length);
                    } else {
                        // Compression did not yield savings; store RAW
                        filePayload = new byte[uncompressedData.length + 1];
                        filePayload[0] = MODE_RAW;
                        System.arraycopy(uncompressedData, 0, filePayload, 1, uncompressedData.length);
                    }
                }
            }

            Path tempFile = tmpDir.resolve(UUID.randomUUID() + ".tmp");
            Files.write(tempFile, filePayload, StandardOpenOption.CREATE, StandardOpenOption.WRITE);

            try {
                Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            return filePayload.length;
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
            byte[] fileBytes = Files.readAllBytes(path);
            if (fileBytes.length == 0) {
                return new byte[0];
            }

            // Check if legacy direct Zstandard frame (without mode prefix)
            if (fileBytes.length >= 4 && fileBytes[0] == ZSTD_MAGIC_BYTE_0 && fileBytes[1] == ZSTD_MAGIC_BYTE_1) {
                long originalSize = Zstd.decompressedSize(fileBytes);
                return Zstd.decompress(fileBytes, (int) originalSize);
            }

            byte mode = fileBytes[0];
            byte[] payload = Arrays.copyOfRange(fileBytes, 1, fileBytes.length);

            if (mode == MODE_RAW) {
                return payload;
            } else if (mode == MODE_ZSTD) {
                long originalSize = Zstd.decompressedSize(payload);
                if (originalSize < 0) {
                    throw new IllegalStateException("Corrupted Zstd frame for chunk: " + hash);
                }
                return Zstd.decompress(payload, (int) originalSize);
            } else {
                throw new IllegalStateException("Unknown chunk storage header mode: " + mode + " for " + hash);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read CAS chunk: " + hash, e);
        }
    }

    public static double calculateEntropy(byte[] data) {
        if (data == null || data.length == 0) return 0.0;
        int sampleLen = Math.min(data.length, 4096);
        int[] counts = new int[256];
        for (int i = 0; i < sampleLen; i++) {
            counts[data[i] & 0xFF]++;
        }
        double entropy = 0.0;
        for (int count : counts) {
            if (count > 0) {
                double p = (double) count / sampleLen;
                entropy -= p * (Math.log(p) / Math.log(2));
            }
        }
        return entropy;
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
