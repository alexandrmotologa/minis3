# S3 API Compatibility

MiniS3 targets compatibility with core AWS S3 REST API specifications used by official SDKs (AWS SDK for Java v2, Python `boto3`, Go SDK, `aws-cli`, and `rclone`).

## Supported endpoints

### Bucket operations

| Operation | HTTP Method | Path | Description |
|---|---|---|---|
| `ListBuckets` | `GET` | `/` | Returns XML listing of all buckets owned by the account. |
| `CreateBucket` | `PUT` | `/{bucket}` | Creates a new bucket with DNS compliant name. |
| `HeadBucket` | `HEAD` | `/{bucket}` | Checks if a bucket exists. Returns 200 or 404. |
| `DeleteBucket` | `DELETE` | `/{bucket}` | Deletes an empty bucket. Returns 409 if objects remain. |

### Object operations

| Operation | HTTP Method | Path | Description |
|---|---|---|---|
| `PutObject` | `PUT` | `/{bucket}/{key}` | Uploads an object. Computes hash, deduplicates, and returns ETag. |
| `GetObject` | `GET` | `/{bucket}/{key}` | Retrieves an object. Supports `Range: bytes=start-end`. |
| `HeadObject` | `HEAD` | `/{bucket}/{key}` | Retrieves object metadata (`Content-Length`, `ETag`, `Content-Type`). |
| `DeleteObject` | `DELETE` | `/{bucket}/{key}` | Deletes an object and decrements chunk reference counters. |
| `ListObjectsV2` | `GET` | `/{bucket}?list-type=2` | Paginated listing of objects with `prefix`, `max-keys`, and `continuation-token`. |

### Multipart upload operations

| Operation | HTTP Method | Path | Description |
|---|---|---|---|
| `CreateMultipartUpload` | `POST` | `/{bucket}/{key}?uploads` | Initiates a multipart session, returning an `UploadId`. |
| `UploadPart` | `PUT` | `/{bucket}/{key}?uploadId={id}&partNumber={n}` | Ingests an individual part and returns its ETag. |
| `CompleteMultipartUpload`| `POST` | `/{bucket}/{key}?uploadId={id}` | Assembles recorded parts into an S3Object manifest. |
| `AbortMultipartUpload` | `DELETE` | `/{bucket}/{key}?uploadId={id}` | Cleans up uploaded parts and terminates the session. |
| `ListParts` | `GET` | `/{bucket}/{key}?uploadId={id}` | Lists parts uploaded so far for the active session. |

## Authentication: AWS Signature Version 4

MiniS3 supports AWS Signature Version 4 (SigV4) header authentication:

```
Authorization: AWS4-HMAC-SHA256
  Credential=<access-key>/<date>/<region>/s3/aws4_request,
  SignedHeaders=<headers>,
  Signature=<64-byte-hex-hmac>
```

### Signature validation flow

1. Parse the `Authorization` header and extract access key, date, region, signed headers, and provided signature.
2. Read request method, normalized URI path, query parameters, and signed headers to reconstruct the Canonical Request.
3. Compute SHA-256 of the Canonical Request.
4. Construct the String to Sign using the timestamp and credential scope.
5. Derive the signing key:
   ```
   kDate    = HMAC-SHA256("AWS4" + SecretKey, "YYYYMMDD")
   kRegion  = HMAC-SHA256(kDate, Region)
   kService = HMAC-SHA256(kRegion, "s3")
   kSigning = HMAC-SHA256(kService, "aws4_request")
   ```
6. Compute HMAC-SHA256 of the String to Sign using `kSigning` and compare with the provided signature.

Presigned URLs (`X-Amz-Signature` query parameters) are verified using the same canonical request specification.

Anonymous access can be enabled in `application.yml` for simplified local testing.
