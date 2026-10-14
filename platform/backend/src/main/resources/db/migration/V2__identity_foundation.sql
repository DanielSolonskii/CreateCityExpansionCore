CREATE TABLE platform_user (
    id UUID PRIMARY KEY,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_platform_user_timestamps CHECK (updated_at >= created_at)
);

CREATE TABLE minecraft_identity (
    minecraft_uuid UUID PRIMARY KEY,
    platform_user_id UUID NOT NULL,
    current_name VARCHAR(16) NOT NULL,
    CONSTRAINT uq_minecraft_identity_platform_user UNIQUE (platform_user_id),
    CONSTRAINT fk_minecraft_identity_platform_user
        FOREIGN KEY (platform_user_id) REFERENCES platform_user (id),
    CONSTRAINT ck_minecraft_identity_name
        CHECK (CHAR_LENGTH(TRIM(current_name)) BETWEEN 1 AND 16)
);

-- Keep the infrastructure marker aligned with the latest migration.
UPDATE platform_infrastructure SET schema_version = 2 WHERE id = 1;
