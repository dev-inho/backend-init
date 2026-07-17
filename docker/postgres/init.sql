-- backend-init 로컬 검증용 초기 스키마
CREATE TABLE IF NOT EXISTS sample (
    id   VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS app_user (
    id           VARCHAR(64) PRIMARY KEY,
    email        VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL
);

INSERT INTO sample (id, name) VALUES ('sample_0001', 'hello')
ON CONFLICT (id) DO NOTHING;

INSERT INTO app_user (id, email, display_name)
VALUES ('user_0001', 'user@example.com', 'Example User')
ON CONFLICT (id) DO NOTHING;
