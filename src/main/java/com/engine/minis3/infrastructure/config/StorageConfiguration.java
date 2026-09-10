package com.engine.minis3.infrastructure.config;

import com.engine.minis3.domain.model.ChunkingStrategy;
import com.engine.minis3.domain.port.ChunkerPort;
import com.engine.minis3.infrastructure.adapter.out.storage.FastCdcChunker;
import com.engine.minis3.infrastructure.adapter.out.storage.FixedSizeChunker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfiguration {

    @Bean
    public ChunkerPort chunkerPort(MiniS3Properties properties) {
        int targetSize = properties.getStorage().getChunkSize();
        if (properties.getStorage().getChunkingStrategy() == ChunkingStrategy.FAST_CDC) {
            int minSize = Math.max(1024, targetSize / 4);
            int maxSize = targetSize * 2;
            return new FastCdcChunker(minSize, targetSize, maxSize);
        } else {
            return new FixedSizeChunker(targetSize);
        }
    }
}
