CREATE TABLE server_bridge_boot (
 boot_id UUID PRIMARY KEY, server_id VARCHAR(64) NOT NULL, started_at TIMESTAMP WITH TIME ZONE NOT NULL,
 revision BIGINT NOT NULL CHECK(revision >= 0), observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
 received_at TIMESTAMP WITH TIME ZONE NOT NULL, online BOOLEAN NOT NULL, uptime_seconds BIGINT NOT NULL CHECK(uptime_seconds >= 0),
 players TEXT NOT NULL, max_players INTEGER NOT NULL CHECK(max_players BETWEEN 1 AND 1000),
 minecraft_version VARCHAR(64) NOT NULL, ccec_version VARCHAR(64) NOT NULL, server_version VARCHAR(128) NOT NULL,
 dropped_events BIGINT NOT NULL CHECK(dropped_events >= 0)
);
CREATE INDEX ix_bridge_latest ON server_bridge_boot(server_id,started_at);
CREATE TABLE telemetry_batch (
 id UUID PRIMARY KEY, boot_id UUID NOT NULL REFERENCES server_bridge_boot(boot_id),
 request_hash VARCHAR(64) NOT NULL, received_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE telemetry_event (
 id UUID PRIMARY KEY, boot_id UUID NOT NULL REFERENCES server_bridge_boot(boot_id),
 platform_user_id UUID REFERENCES platform_user(id), minecraft_uuid UUID NOT NULL,
 type VARCHAR(40) NOT NULL, subject_id UUID, detail VARCHAR(64) NOT NULL,
 occurred_at TIMESTAMP WITH TIME ZONE NOT NULL, received_at TIMESTAMP WITH TIME ZONE NOT NULL,
 duration_seconds BIGINT NOT NULL CHECK(duration_seconds >= 0), request_hash VARCHAR(64) NOT NULL
);
CREATE INDEX ix_telemetry_time ON telemetry_event(occurred_at,type);
CREATE INDEX ix_telemetry_user ON telemetry_event(platform_user_id,occurred_at);
CREATE TABLE telemetry_session (
 id UUID PRIMARY KEY, boot_id UUID NOT NULL REFERENCES server_bridge_boot(boot_id),
 platform_user_id UUID REFERENCES platform_user(id), minecraft_uuid UUID NOT NULL,
 started_at TIMESTAMP WITH TIME ZONE NOT NULL, ended_at TIMESTAMP WITH TIME ZONE,
 duration_seconds BIGINT NOT NULL CHECK(duration_seconds >= 0)
);
UPDATE platform_infrastructure SET schema_version=9 WHERE id=1;
