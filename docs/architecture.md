# Architecture

MiniS3 follows a hexagonal architecture pattern (ports and adapters). The goal is to keep the storage model and business logic decoupled from HTTP frameworks, database drivers, and disk serialization details.

## Layer boundaries

```
com.engine.minis3/
├── domain/                          # Pure Java 21 models, ports, and invariants
│   ├── model/                       # Bucket, S3Object, ChunkHash, Chunk, MultipartUpload
│   ├── auth/                        # SigV4Credentials, CanonicalRequest, SigV4Validator
│   ├── exception/                   # Domain exceptions (NoSuchBucketException, etc.)
│   └── port/                        # Secondary interfaces for persistence and chunk I/O
├── application/                     # Application services and use cases
│   ├── service/                     # BucketService, ObjectService, MultipartService, TelemetryService
│   └── dto/                         # Internal transfer models and S3 XML representations
└── infrastructure/                  # Primary and secondary adapters
    ├── adapter/
    │   ├── in/rest/                 # Spring MVC S3 REST controllers (XML and binary streaming)
    │   ├── in/auth/                 # AWS SigV4 authentication filter and presigned handler
    │   ├── out/storage/             # Filesystem content-addressable storage with Zstd
    │   └── out/metadata/            # SQLite metadata engine with HikariCP and WAL mode
    ├── config/                      # Virtual thread executor and application properties
    └── web/                         # Administrative dashboard controller and static assets
```

### Domain layer

The domain layer contains pure Java classes without framework annotations or third-party dependencies:
- `Bucket`: Represents an S3 bucket container and enforces valid DNS naming rules.
- `S3Object`: Represents an object stored in a bucket, holding key, size, ETag, content type, creation time, and an ordered list of SHA-256 chunk references.
- `ChunkHash`: Value object wrapping a 64-character lowercase hexadecimal SHA-256 hash. It calculates sharded filesystem paths (e.g., `ab/cd/abcdef...`).
- `Chunk`: Represents a physical slice of content, its uncompressed byte length, compressed byte length, and reference count.
- `Ports`: Define SPI interfaces (`BucketRepositoryPort`, `ObjectMetadataPort`, `ChunkStorePort`) implemented by infrastructure adapters.

### Application layer

The application layer coordinates workflows:
- `BucketService`: Handles bucket lifecycle rules, ensuring non-empty buckets cannot be deleted unless requested.
- `ObjectService`: Orchestrates stream intake, chunk hashing, deduplication checks, Zstd compression, and object metadata indexing. It also handles byte-range slicing for partial content requests.
- `MultipartService`: Manages multipart lifecycle states (initiation, part staging, assembly, and abort cleanup).
- `TelemetryService`: Aggregates storage statistics, comparing raw uploaded bytes against physical compressed disk usage to calculate deduplication efficiency.

### Infrastructure layer

The infrastructure layer adapts external protocols and persistence mechanisms:
- Inbound REST: Exposes AWS S3 compliant XML endpoints and handles raw binary streams.
- Inbound Auth: Intercepts requests, validates timestamps and canonical request signatures, and verifies HMAC-SHA256 signatures.
- Outbound Metadata: Implements SQLite storage with Write-Ahead Logging (WAL) and connection pooling.
- Outbound Storage: Manages content-addressed files under `.minis3/chunks/` with atomic writes.
- Embedded Console: Serves the administrative single-page dashboard directly from the classpath.

## Virtual thread concurrency

MiniS3 runs on Java 21 with virtual threads enabled (`spring.threads.virtual.enabled=true`).

Every incoming HTTP connection is dispatched to a lightweight virtual thread. When an upload or download streams bytes to or from disk, the underlying thread unmounts from its carrier during blocking file system and socket operations. This provides high concurrent throughput with predictable memory consumption.
