# Content-Addressable Storage and Deduplication

MiniS3 uses Content-Addressable Storage (CAS) to decouple object identity from the storage location on disk.

## How CAS works in MiniS3

When a client uploads an object to `PUT /{bucket}/{key}`, MiniS3 processes the incoming stream through the following pipeline:

```
[ Incoming Stream ]
         |
         v
[ Deterministic Chunking (4MB Fixed Boundary) ]
         |
         v
[ Compute SHA-256 Digest for Each Chunk ]
         |
         +-----------------------------+
         |                             |
         v                             v
[ Digest exists in SQLite index? ]     [ New Digest ]
         |                             |
         v (Deduplication Hit)         v (Cache Miss)
[ Increment ref_count in SQLite ]     [ Compress with Zstandard ]
                                       |
                                       v
                              [ Write to .minis3/chunks/ab/cd/<hash>.zstd ]
                                       |
                                       v
                              [ Insert into chunks & object_chunks table ]
```

### Deterministic chunking

- Payloads of 4 MB or less are treated as a single chunk. This eliminates chunking overhead for small files and metadata documents.
- Payloads exceeding 4 MB are sliced into deterministic 4 MB blocks. Each block is hashed independently.
- If two distinct files share identical blocks (such as consecutive log files, VM snapshots, or database backups), identical blocks are stored once.

### Filesystem sharding

To prevent filesystem directories from exceeding inode lookups on large installations, chunks are stored using a two-level prefix scheme:

```
.minis3/
└── chunks/
    ├── e3/
    │   └── b0/
    │       └── e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855.zstd
    └── a1/
        └── c4/
            └── a1c448f2b79e954c2d338f0d55e003112c3093f1bc41a27e7f7f8cf28f413d98.zstd
```

### Atomicity and crash safety

When saving a new chunk:
1. Data is written to a temporary file in `.minis3/tmp/` with an active Zstd compression stream.
2. The SHA-256 hash is verified against the written bytes.
3. The temporary file is renamed to its target CAS path using `Files.move` with `ATOMIC_MOVE`.
4. The chunk reference is recorded in the SQLite database within a transaction.

If the server crashes mid-write, partial files in `.minis3/tmp/` are purged on startup and the database remains consistent.

### Zstandard compression

MiniS3 uses `com.github.luben:zstd-jni` at compression level 3. This provides fast compression speeds (hundreds of megabytes per second) while delivering strong compression ratios on structured text, logs, and JSON. Pre-compressed formats like JPEG or MP4 still benefit from hash deduplication.

### Reference counting and deletion

- Each chunk in the SQLite database tracks a `ref_count` column.
- When an object is deleted (`DELETE /{bucket}/{key}`), its associated chunk records have their reference counters decremented.
- Chunks whose reference count reaches zero can be purged immediately or reclaimed via the administrative garbage collection endpoint (`POST /api/admin/gc`).
