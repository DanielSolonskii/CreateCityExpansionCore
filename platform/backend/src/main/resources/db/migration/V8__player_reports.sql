CREATE TABLE player_report (
 id UUID PRIMARY KEY,
 platform_user_id UUID NOT NULL REFERENCES platform_user(id),
 minecraft_uuid UUID NOT NULL REFERENCES minecraft_identity(minecraft_uuid),
 username VARCHAR(32) NOT NULL,
 server_id VARCHAR(64) NOT NULL,
 text VARCHAR(1000) NOT NULL CHECK (CHAR_LENGTH(text) BETWEEN 5 AND 1000),
 dimension VARCHAR(128) NOT NULL,
 x DOUBLE PRECISION NOT NULL, y DOUBLE PRECISION NOT NULL, z DOUBLE PRECISION NOT NULL,
 occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
 request_hash VARCHAR(64) NOT NULL,
 status VARCHAR(16) NOT NULL CHECK (status IN ('OPEN','IN_PROGRESS','CLOSED','REJECTED')),
 assigned_admin BIGINT CHECK (assigned_admin > 0),
 CHECK (status <> 'IN_PROGRESS' OR assigned_admin IS NOT NULL),
 CHECK (updated_at >= created_at)
);
CREATE INDEX ix_report_user_time ON player_report(platform_user_id,created_at);
CREATE INDEX ix_report_open_time ON player_report(status,created_at,id);
CREATE TABLE report_admin_action (
 id UUID PRIMARY KEY,
 report_id UUID NOT NULL REFERENCES player_report(id),
 admin_telegram_id BIGINT NOT NULL CHECK (admin_telegram_id > 0),
 action VARCHAR(16) NOT NULL CHECK (action IN ('TAKE','CLOSE','REJECT')),
 previous_status VARCHAR(16) NOT NULL,
 resulting_status VARCHAR(16) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_report_action_history ON report_admin_action(report_id,created_at);
CREATE TABLE report_notification (
 report_id UUID NOT NULL REFERENCES player_report(id),
 admin_telegram_id BIGINT NOT NULL CHECK (admin_telegram_id > 0),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 lease_token UUID,
 lease_until TIMESTAMP WITH TIME ZONE,
 delivered_at TIMESTAMP WITH TIME ZONE,
 PRIMARY KEY(report_id,admin_telegram_id),
 CHECK ((lease_token IS NULL) = (lease_until IS NULL))
);
UPDATE platform_infrastructure SET schema_version=8 WHERE id=1;
