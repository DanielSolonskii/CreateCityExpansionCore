-- Replace the inline V2 status check without relying on a vendor-generated constraint name.
-- Existing status values survive; V1/V2 checksums remain unchanged.
ALTER TABLE platform_user RENAME COLUMN status TO status_v2;
ALTER TABLE platform_user ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'PENDING_VERIFICATION'
    CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'DISABLED'));
UPDATE platform_user SET status = status_v2;
ALTER TABLE platform_user DROP COLUMN status_v2;
ALTER TABLE platform_user ALTER COLUMN status DROP DEFAULT;

CREATE TABLE account_credentials (
    platform_user_id UUID PRIMARY KEY REFERENCES platform_user(id),
    username VARCHAR(32) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    CONSTRAINT ck_account_username CHECK (
        username = LOWER(username) AND CHAR_LENGTH(username) BETWEEN 3 AND 32),
    CONSTRAINT ck_account_password_hash CHECK (CHAR_LENGTH(password_hash) >= 60)
);

CREATE TABLE telegram_identity (
    telegram_user_id BIGINT PRIMARY KEY CHECK (telegram_user_id > 0),
    platform_user_id UUID NOT NULL UNIQUE REFERENCES platform_user(id),
    current_username VARCHAR(64),
    verified_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE registration_session (
    id UUID PRIMARY KEY,
    platform_user_id UUID NOT NULL UNIQUE REFERENCES platform_user(id),
    verification_hash VARCHAR(64) NOT NULL UNIQUE CHECK (CHAR_LENGTH(verification_hash) = 64),
    polling_hash VARCHAR(64) NOT NULL UNIQUE CHECK (CHAR_LENGTH(polling_hash) = 64),
    state VARCHAR(16) NOT NULL CHECK (state IN ('PENDING', 'COMPLETED', 'EXPIRED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_registration_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_registration_separate_secrets CHECK (verification_hash <> polling_hash),
    CONSTRAINT ck_registration_completion CHECK (
        (state = 'COMPLETED' AND completed_at IS NOT NULL) OR
        (state <> 'COMPLETED' AND completed_at IS NULL))
);
UPDATE platform_infrastructure SET schema_version = 3 WHERE id = 1;
