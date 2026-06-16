-- backend-init 로컬 검증용 초기 스키마
CREATE TABLE IF NOT EXISTS sample (
    id   VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

INSERT INTO sample (id, name) VALUES ('sample_0001', 'hello')
ON CONFLICT (id) DO NOTHING;
