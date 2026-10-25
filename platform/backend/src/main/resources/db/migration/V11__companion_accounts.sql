CREATE TABLE companion_preferences (
 platform_user_id UUID PRIMARY KEY REFERENCES platform_user(id),
 news BOOLEAN NOT NULL DEFAULT TRUE, maintenance BOOLEAN NOT NULL DEFAULT TRUE,
 reports BOOLEAN NOT NULL DEFAULT TRUE, updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
-- Last authoritative full projection per server. No position, inventory or credentials.
CREATE TABLE companion_snapshot (
 server_id VARCHAR(64) PRIMARY KEY, boot_id UUID NOT NULL,
 observed_at TIMESTAMP WITH TIME ZONE NOT NULL, payload TEXT NOT NULL
);
CREATE TABLE companion_profile (
 minecraft_uuid UUID PRIMARY KEY, about VARCHAR(180) NOT NULL,
 observed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE companion_notice (
 action_id UUID PRIMARY KEY REFERENCES report_admin_action(id),
 telegram_user_id BIGINT NOT NULL, text VARCHAR(500) NOT NULL,
 state VARCHAR(16) NOT NULL CHECK(state IN ('SENDING','SENT','FAILED','UNCERTAIN')),
 attempt_token UUID NOT NULL, started_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE companion_notice_start (id INTEGER PRIMARY KEY CHECK(id=1), started_at TIMESTAMP WITH TIME ZONE NOT NULL);
INSERT INTO companion_notice_start(id,started_at) VALUES(1,CURRENT_TIMESTAMP);
UPDATE platform_infrastructure SET schema_version=11 WHERE id=1;
