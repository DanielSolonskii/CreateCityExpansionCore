ALTER TABLE companion_profile ADD COLUMN play_ticks BIGINT;
ALTER TABLE companion_profile ADD COLUMN skin_url VARCHAR(255);
ALTER TABLE companion_profile ADD COLUMN stats_observed_at TIMESTAMP WITH TIME ZONE;
UPDATE platform_infrastructure SET schema_version=13 WHERE id=1;
