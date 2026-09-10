-- MiniS3 Metadata Schema for SQLite with Versioning & Multi-Credential support

CREATE TABLE IF NOT EXISTS buckets (
    name TEXT PRIMARY KEY,
    created_at TEXT NOT NULL,
    versioning_status TEXT NOT NULL DEFAULT 'Off'
);

CREATE TABLE IF NOT EXISTS objects (
    bucket_name TEXT NOT NULL,
    key TEXT NOT NULL,
    version_id TEXT NOT NULL DEFAULT 'null',
    is_latest INTEGER NOT NULL DEFAULT 1,
    is_delete_marker INTEGER NOT NULL DEFAULT 0,
    size INTEGER NOT NULL,
    etag TEXT NOT NULL,
    content_type TEXT NOT NULL,
    created_at TEXT NOT NULL,
    PRIMARY KEY (bucket_name, key, version_id),
    FOREIGN KEY (bucket_name) REFERENCES buckets(name) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_objects_lookup ON objects(bucket_name, key, is_latest);
CREATE INDEX IF NOT EXISTS idx_objects_versions ON objects(bucket_name, key, version_id);

CREATE TABLE IF NOT EXISTS chunks (
    hash TEXT PRIMARY KEY,
    raw_size INTEGER NOT NULL,
    compressed_size INTEGER NOT NULL,
    ref_count INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS object_chunks (
    bucket_name TEXT NOT NULL,
    object_key TEXT NOT NULL,
    version_id TEXT NOT NULL DEFAULT 'null',
    chunk_order INTEGER NOT NULL,
    chunk_hash TEXT NOT NULL,
    chunk_offset INTEGER NOT NULL,
    chunk_length INTEGER NOT NULL,
    PRIMARY KEY (bucket_name, object_key, version_id, chunk_order),
    FOREIGN KEY (chunk_hash) REFERENCES chunks(hash)
);

CREATE INDEX IF NOT EXISTS idx_object_chunks_lookup ON object_chunks(bucket_name, object_key, version_id);

CREATE TABLE IF NOT EXISTS multipart_uploads (
    upload_id TEXT PRIMARY KEY,
    bucket_name TEXT NOT NULL,
    object_key TEXT NOT NULL,
    content_type TEXT NOT NULL,
    initiated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS multipart_parts (
    upload_id TEXT NOT NULL,
    part_number INTEGER NOT NULL,
    etag TEXT NOT NULL,
    size INTEGER NOT NULL,
    chunk_hash TEXT NOT NULL,
    uploaded_at TEXT NOT NULL,
    PRIMARY KEY (upload_id, part_number),
    FOREIGN KEY (upload_id) REFERENCES multipart_uploads(upload_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS api_credentials (
    access_key TEXT PRIMARY KEY,
    secret_key TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'ADMIN',
    allowed_buckets TEXT DEFAULT '*',
    created_at TEXT NOT NULL
);
