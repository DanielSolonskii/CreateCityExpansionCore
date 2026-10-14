-- Administrative broadcasts are durable Backend data, not Telegram presentation state.
CREATE TABLE broadcast_lock (id INTEGER PRIMARY KEY CHECK (id = 1));
INSERT INTO broadcast_lock(id) VALUES (1);
CREATE TABLE broadcast_post (
    id UUID PRIMARY KEY,
    creator_admin BIGINT NOT NULL CHECK (creator_admin > 0),
    text VARCHAR(4000) NOT NULL,
    photo_file_id VARCHAR(512),
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT','QUEUED','COMPLETED','CANCELLED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    confirmed_at TIMESTAMP WITH TIME ZONE,
    CHECK (CHAR_LENGTH(text) > 0 OR photo_file_id IS NOT NULL),
    CHECK (photo_file_id IS NULL OR CHAR_LENGTH(text) <= 1024)
);
CREATE TABLE broadcast_delivery (
    post_id UUID NOT NULL REFERENCES broadcast_post(id),
    telegram_user_id BIGINT NOT NULL CHECK (telegram_user_id > 0),
    state VARCHAR(16) NOT NULL CHECK (state IN ('PENDING','SENDING','SENT','FAILED','UNCERTAIN')),
    attempt_token UUID,
    started_at TIMESTAMP WITH TIME ZONE,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    telegram_message_id BIGINT,
    failure_code VARCHAR(32),
    PRIMARY KEY (post_id, telegram_user_id)
);
CREATE INDEX ix_broadcast_pending ON broadcast_delivery(state,next_attempt_at);
UPDATE platform_infrastructure SET schema_version=10 WHERE id=1;
