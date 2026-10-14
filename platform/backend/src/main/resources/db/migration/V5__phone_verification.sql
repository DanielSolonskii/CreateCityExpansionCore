ALTER TABLE registration_session ADD COLUMN code_hash VARCHAR(64) UNIQUE;
ALTER TABLE registration_session ADD COLUMN restart_count INTEGER NOT NULL DEFAULT 0 CHECK (restart_count BETWEEN 0 AND 3);
ALTER TABLE registration_session ADD CONSTRAINT registration_code_hash_length CHECK (code_hash IS NULL OR LENGTH(code_hash) = 64);
CREATE TABLE telegram_confirmation (
    token_hash VARCHAR(64) PRIMARY KEY CHECK (LENGTH(token_hash) = 64),
    registration_id UUID NOT NULL REFERENCES registration_session(id) ON DELETE CASCADE,
    verification_hash VARCHAR(64) NOT NULL,
    telegram_user_id BIGINT NOT NULL CHECK (telegram_user_id > 0),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX telegram_confirmation_registration ON telegram_confirmation(registration_id);
CREATE TABLE verification_rate_bucket (
    bucket_key VARCHAR(64) PRIMARY KEY,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempts INTEGER NOT NULL CHECK (attempts > 0)
);
UPDATE platform_infrastructure SET schema_version = 5 WHERE id = 1;
