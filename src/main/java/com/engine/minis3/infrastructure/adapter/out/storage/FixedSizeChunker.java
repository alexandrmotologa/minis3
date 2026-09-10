package com.engine.minis3.infrastructure.adapter.out.storage;

import com.engine.minis3.domain.port.ChunkerPort;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fixed-size chunking implementation dividing data into uniform blocks.
 */
public class FixedSizeChunker implements ChunkerPort {

    private final int chunkSize;

    public FixedSizeChunker(int chunkSize) {
        this.chunkSize = Math.max(1024, chunkSize);
    }

    public FixedSizeChunker() {
        this(4194304); // 4 MB default
    }

    @Override
    public List<byte[]> chunk(byte[] data) {
        List<byte[]> chunks = new ArrayList<>();
        if (data == null || data.length == 0) {
            chunks.add(new byte[0]);
            return chunks;
        }

        int offset = 0;
        while (offset < data.length) {
            int len = Math.min(chunkSize, data.length - offset);
            chunks.add(Arrays.copyOfRange(data, offset, offset + len));
            offset += len;
        }

        return chunks;
    }
}
