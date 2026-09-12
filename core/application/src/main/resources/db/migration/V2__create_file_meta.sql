CREATE TABLE file_meta (
    id VARCHAR(26) PRIMARY KEY,
    owner_id VARCHAR(100) NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    size_bytes BIGINT,
    content_type VARCHAR(100),
    checksum VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_file_meta_storage_key ON file_meta (storage_key);
CREATE INDEX idx_file_meta_status_updated ON file_meta (status, updated_at);
