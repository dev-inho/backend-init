CREATE TABLE IF NOT EXISTS sample (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS app_user (
    id VARCHAR(64) PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS scalar_sample (
    id VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    nickname VARCHAR(255),
    status VARCHAR(255) NOT NULL,
    code_value VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS relation_parent (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS relation_child (
    id VARCHAR(64) PRIMARY KEY,
    parent_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    CONSTRAINT fk_relation_child_parent
        FOREIGN KEY (parent_id)
        REFERENCES relation_parent (id)
);

CREATE INDEX IF NOT EXISTS idx_relation_child_parent_id
    ON relation_child (parent_id);

INSERT INTO sample (id, name)
VALUES ('sample_0001', 'hello')
ON CONFLICT (id) DO NOTHING;

INSERT INTO app_user (id, email, display_name)
VALUES ('user_0001', 'user@example.com', 'Example User')
ON CONFLICT (id) DO NOTHING;
