# MiniS3

MiniS3 is a lightweight S3-compatible content-addressable storage (CAS) engine written in Java 21 LTS using Spring Boot 3.3 and Virtual Threads. It implements the AWS S3 REST API and AWS Signature Version 4 authentication while deduplicating identical object payloads across buckets using SHA-256 content addressing and Zstandard compression.

## Why MiniS3 exists

Local development environments and integration test pipelines frequently require object storage to simulate AWS S3. Standard local alternatives like MinIO have grown heavy, memory intensive, and complex to bundle in small developer environments.

Standard object stores also write data redundantly to disk. If multiple services or test suites upload identical 50 MB artifacts, test fixtures, or container layers with different keys, the storage engine writes duplicate bytes each time.

MiniS3 addresses this with a content-addressable storage core:
- Payloads are indexed by SHA-256 hash before disk writes.
- If an identical payload already exists in any bucket, MiniS3 increments a reference counter and skips the physical write.
- Unique payloads are compressed on ingestion using native Zstandard (`zstd-jni`), cutting disk footprint by 40% to 70% on typical JSON, text, and binary formats.
- An embedded web console provides visual bucket exploration and live telemetry on deduplication savings.

## Key capabilities

- AWS S3 compatibility: Implements core endpoints including bucket creation, single object PUT and GET, byte-range retrieval (`Range: bytes=start-end`), object deletion, and `ListObjectsV2` pagination.
- AWS Signature Version 4 (SigV4): Authenticates requests signed with HMAC-SHA256 credentials, including canonical request verification and presigned URLs.
- Content-addressable deduplication: Slices objects into deterministic SHA-256 chunks with cross-bucket deduplication.
- Transparent Zstandard compression: Native compression and decompression using high performance Zstd.
- S3 multipart uploads: Supports initiating, uploading parts, completing, and aborting large chunked uploads.
- Embedded web dashboard: Single-page administrative dashboard running on port 9000 showing bucket contents, live upload speeds, and real-time storage savings.
- Java 21 Virtual Threads: Handles concurrent streaming I/O with high throughput and low memory overhead.

## Architecture

MiniS3 follows hexagonal architecture (ports and adapters) to isolate core business rules from storage drivers and HTTP layers:

- `domain`: Pure Java 21 models (`Bucket`, `S3Object`, `ChunkHash`, `Chunk`) and business exceptions. It contains zero framework dependencies.
- `application`: Use cases for bucket management, object streaming, multipart assembly, and administrative telemetry.
- `infrastructure`: Inbound REST controllers, SigV4 authentication filters, SQLite metadata repository (WAL mode), Zstandard CAS chunk store, and embedded web console.

For more details, see [docs/architecture.md](docs/architecture.md), [docs/cas-deduplication.md](docs/cas-deduplication.md), and [docs/s3-api-compatibility.md](docs/s3-api-compatibility.md).

## Quick start

### Prerequisites

- Java 21 LTS
- Maven 3.9+

### Build and run

```bash
mvn clean package
java -jar target/minis3-1.0.0-SNAPSHOT.jar
```

The server starts on port `9000` with default development credentials:
- Access Key: `minis3-access-key`
- Secret Key: `minis3-secret-key`
- Region: `us-east-1`

Open `http://localhost:9000` in a web browser to view the administrative console.

### Configuration

You can configure MiniS3 through `application.yml` or environment variables:

| Environment Variable | Default Value | Description |
|---|---|---|
| `MINIS3_PORT` | `9000` | HTTP server listening port |
| `MINIS3_DATA_DIR` | `./.minis3` | Base directory for chunks and SQLite metadata |
| `MINIS3_ACCESS_KEY` | `minis3-access-key` | AWS SigV4 root access key |
| `MINIS3_SECRET_KEY` | `minis3-secret-key` | AWS SigV4 root secret key |
| `MINIS3_REGION` | `us-east-1` | Default AWS region |

### Using with AWS CLI

Configure a profile or pass credentials inline:

```bash
export AWS_ACCESS_KEY_ID=minis3-access-key
export AWS_SECRET_ACCESS_KEY=minis3-secret-key
export AWS_DEFAULT_REGION=us-east-1

# Create a bucket
aws --endpoint-url=http://localhost:9000 s3 mb s3://my-bucket

# Upload a file
aws --endpoint-url=http://localhost:9000 s3 cp dataset.csv s3://my-bucket/dataset.csv

# Upload the same file under a different key (triggers deduplication)
aws --endpoint-url=http://localhost:9000 s3 cp dataset.csv s3://my-bucket/dataset-backup.csv

# List objects
aws --endpoint-url=http://localhost:9000 s3 ls s3://my-bucket/

# Download an object
aws --endpoint-url=http://localhost:9000 s3 cp s3://my-bucket/dataset.csv downloaded.csv
```

### Using with Python (Boto3)

```python
import boto3

s3 = boto3.client(
    "s3",
    endpoint_url="http://localhost:9000",
    aws_access_key_id="minis3-access-key",
    aws_secret_access_key="minis3-secret-key",
    region_name="us-east-1",
)

s3.create_bucket(Bucket="demo-bucket")
s3.put_object(Bucket="demo-bucket", Key="hello.txt", Body=b"Hello from MiniS3")

response = s3.get_object(Bucket="demo-bucket", Key="hello.txt")
print(response["Body"].read().decode("utf-8"))
```

### Using with AWS Java SDK v2

```java
S3Client s3 = S3Client.builder()
    .endpointOverride(URI.create("http://localhost:9000"))
    .region(Region.US_EAST_1)
    .credentialsProvider(StaticCredentialsProvider.create(
        AwsBasicCredentials.create("minis3-access-key", "minis3-secret-key")
    ))
    .forcePathStyle(true)
    .build();

s3.createBucket(b -> b.bucket("sample-bucket"));
s3.putObject(b -> b.bucket("sample-bucket").key("test.json"), RequestBody.fromString("{\"status\":\"ok\"}"));
```

## License

MIT License. See [LICENSE](LICENSE) for terms.
