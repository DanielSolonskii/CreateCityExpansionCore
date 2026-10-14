ALTER TABLE minecraft_identity ADD COLUMN join_name VARCHAR(16);
ALTER TABLE minecraft_identity ADD CONSTRAINT uq_minecraft_join_name UNIQUE (join_name);
ALTER TABLE minecraft_identity ADD CONSTRAINT ck_minecraft_join_name CHECK (join_name IS NULL OR REGEXP_LIKE(join_name, '^cs[a-z0-9]{14}$'));

CREATE TABLE launcher_session (
    id UUID PRIMARY KEY,
    platform_user_id UUID NOT NULL REFERENCES platform_user(id),
    device_id UUID NOT NULL REFERENCES trusted_device(id),
    secret_hash VARCHAR(64) NOT NULL UNIQUE CHECK (CHAR_LENGTH(secret_hash)=64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CHECK (expires_at > created_at)
);
CREATE INDEX ix_launcher_session_user ON launcher_session(platform_user_id,expires_at);

CREATE TABLE game_server_boot (
    server_id VARCHAR(64) PRIMARY KEY,
    boot_id UUID NOT NULL,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE game_session (
    id UUID PRIMARY KEY,
    launcher_session_id UUID NOT NULL REFERENCES launcher_session(id),
    platform_user_id UUID NOT NULL REFERENCES platform_user(id),
    minecraft_uuid UUID NOT NULL REFERENCES minecraft_identity(minecraft_uuid),
    server_id VARCHAR(64) NOT NULL REFERENCES game_server_boot(server_id),
    boot_id UUID NOT NULL,
    connection_id UUID NOT NULL,
    nonce_hash VARCHAR(64) NOT NULL CHECK (CHAR_LENGTH(nonce_hash)=64),
    secret_hash VARCHAR(64) NOT NULL UNIQUE CHECK (CHAR_LENGTH(secret_hash)=64),
    state VARCHAR(16) NOT NULL CHECK (state IN ('ISSUED','CONSUMED','REVOKED','EXPIRED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    consumed_display_name VARCHAR(16),
    CHECK (expires_at > created_at),
    CHECK ((state='CONSUMED' AND consumed_at IS NOT NULL AND consumed_display_name IS NOT NULL)
       OR (state<>'CONSUMED' AND consumed_at IS NULL AND consumed_display_name IS NULL))
);
CREATE INDEX ix_game_session_user_created ON game_session(platform_user_id,created_at);
UPDATE platform_infrastructure SET schema_version=7 WHERE id=1;
