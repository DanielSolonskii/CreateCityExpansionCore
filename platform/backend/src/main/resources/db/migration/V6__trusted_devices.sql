CREATE TABLE trusted_device (
    id UUID PRIMARY KEY,
    platform_user_id UUID NOT NULL REFERENCES platform_user(id) ON DELETE CASCADE,
    label VARCHAR(64) NOT NULL,
    state VARCHAR(16) NOT NULL CHECK (state IN ('PENDING', 'TRUSTED', 'REVOKED')),
    credential_hash VARCHAR(64) NOT NULL UNIQUE CHECK (LENGTH(credential_hash) = 64),
    polling_hash VARCHAR(64) NOT NULL UNIQUE CHECK (LENGTH(polling_hash) = 64),
    verification_hash VARCHAR(64) NOT NULL UNIQUE CHECK (LENGTH(verification_hash) = 64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    confirmation_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    trusted_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    approved_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    CHECK (confirmation_expires_at > created_at AND trusted_expires_at > confirmation_expires_at),
    CHECK (state <> 'TRUSTED' OR approved_at IS NOT NULL),
    CHECK (state <> 'REVOKED' OR revoked_at IS NOT NULL)
);
CREATE INDEX trusted_device_user ON trusted_device(platform_user_id);
UPDATE platform_infrastructure SET schema_version = 6 WHERE id = 1;
