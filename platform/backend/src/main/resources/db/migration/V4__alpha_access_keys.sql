-- Existing accounts/sessions remain unchanged; alpha access applies to new registration.
CREATE TABLE alpha_access_key (
    id UUID PRIMARY KEY,
    key_hash VARCHAR(64) NOT NULL UNIQUE CHECK (CHAR_LENGTH(key_hash) = 64),
    state VARCHAR(16) NOT NULL CHECK (state IN ('UNUSED', 'USED', 'REVOKED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    used_at TIMESTAMP WITH TIME ZONE,
    platform_user_id UUID UNIQUE REFERENCES platform_user(id),
    CONSTRAINT ck_alpha_expiry CHECK (expires_at IS NULL OR expires_at > created_at),
    CONSTRAINT ck_alpha_usage CHECK (
        (state = 'USED' AND used_at IS NOT NULL AND platform_user_id IS NOT NULL AND used_at >= created_at) OR
        (state IN ('UNUSED', 'REVOKED') AND used_at IS NULL AND platform_user_id IS NULL))
);
UPDATE platform_infrastructure SET schema_version = 4 WHERE id = 1;
