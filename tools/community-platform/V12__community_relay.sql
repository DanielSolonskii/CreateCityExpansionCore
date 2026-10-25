CREATE TABLE community_notice (
 id UUID PRIMARY KEY, owner_uuid UUID NOT NULL, telegram_user_id BIGINT NOT NULL,
 peer_uuid UUID NOT NULL, peer_name VARCHAR(64) NOT NULL, kind VARCHAR(16) NOT NULL,
 text VARCHAR(500) NOT NULL, city_uuid UUID, invite_token UUID,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, available_at TIMESTAMP WITH TIME ZONE NOT NULL,
 state VARCHAR(16) NOT NULL, attempt_token UUID, started_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX ix_community_notice_pending ON community_notice(state,available_at);
CREATE TABLE community_command (
 id UUID PRIMARY KEY, actor_uuid UUID NOT NULL, telegram_user_id BIGINT NOT NULL, target_uuid UUID NOT NULL,
 kind VARCHAR(16) NOT NULL, text VARCHAR(500) NOT NULL, invite_token UUID,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, state VARCHAR(16) NOT NULL, result VARCHAR(24) NOT NULL
);
CREATE INDEX ix_community_command_pending ON community_command(state,created_at);
CREATE TABLE community_policy (
 owner_uuid UUID PRIMARY KEY, enabled BOOLEAN NOT NULL, preview BOOLEAN NOT NULL,
 offline_only BOOLEAN NOT NULL, online BOOLEAN NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE community_event_receipt (id UUID PRIMARY KEY, created_at TIMESTAMP WITH TIME ZONE NOT NULL);
UPDATE platform_infrastructure SET schema_version=12 WHERE id=1;
