package com.engine.minis3.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@ConfigurationProperties(prefix = "minis3")
public class MiniS3Properties {

    private Storage storage = new Storage();
    private Auth auth = new Auth();

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    public static class Storage {
        private String rootDir = "./.minis3";
        private int chunkSize = 4 * 1024 * 1024; // 4 MB
        private int zstdLevel = 3;
        private com.engine.minis3.domain.model.ChunkingStrategy chunkingStrategy = com.engine.minis3.domain.model.ChunkingStrategy.FAST_CDC;

        public com.engine.minis3.domain.model.ChunkingStrategy getChunkingStrategy() {
            return chunkingStrategy;
        }

        public void setChunkingStrategy(com.engine.minis3.domain.model.ChunkingStrategy chunkingStrategy) {
            this.chunkingStrategy = chunkingStrategy;
        }

        public String getRootDir() {
            return rootDir;
        }

        public void setRootDir(String rootDir) {
            this.rootDir = rootDir;
        }

        public int getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
        }

        public int getZstdLevel() {
            return zstdLevel;
        }

        public void setZstdLevel(int zstdLevel) {
            this.zstdLevel = zstdLevel;
        }

        public Path getRootPath() {
            return Paths.get(rootDir).toAbsolutePath().normalize();
        }

        public Path getChunksPath() {
            return getRootPath().resolve("chunks");
        }

        public Path getTmpPath() {
            return getRootPath().resolve("tmp");
        }

        public Path getDatabasePath() {
            return getRootPath().resolve("metadata.db");
        }
    }

    public static class Auth {
        private String accessKey = "minis3-access-key";
        private String secretKey = "minis3-secret-key";
        private String region = "us-east-1";
        private boolean requireAuth = false;

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public boolean isRequireAuth() {
            return requireAuth;
        }

        public void setRequireAuth(boolean requireAuth) {
            this.requireAuth = requireAuth;
        }
    }
}
