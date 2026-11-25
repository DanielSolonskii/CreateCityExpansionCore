SET TIME ZONE 'Europe/Minsk';
WITH report AS (
    SELECT (now() - interval '21 hours')::date AS day
), days AS (
    SELECT d::date AS day
    FROM report, LATERAL (VALUES (day), (day - 1), (day - 7),
                                  ((day - interval '1 month')::date)) dates(d)
), periods AS (
    SELECT day, day::timestamptz AS start_at, (day + time '21:00')::timestamptz AS stop_at FROM days
), game AS (
    SELECT s.minecraft_uuid, s.started_at
    FROM telemetry_session s JOIN server_bridge_boot b ON b.boot_id = s.boot_id
    WHERE b.server_id = 'recreated-alpha'
), history AS (
    SELECT (SELECT min(started_at) FROM server_bridge_boot WHERE server_id = 'recreated-alpha') AS game_start,
           (SELECT min(occurred_at) FROM launcher_telemetry_event) AS launcher_start
)
SELECT json_agg(json_build_object('day', day,
    'players', CASE WHEN game_start <= start_at THEN g.players END,
    'joins', CASE WHEN game_start <= start_at THEN g.joins END,
    'clicks', CASE WHEN launcher_start <= start_at THEN l.clicks END,
    'failures', CASE WHEN launcher_start <= start_at THEN l.failures END
) ORDER BY day DESC)
FROM periods CROSS JOIN history
CROSS JOIN LATERAL (
    SELECT count(DISTINCT minecraft_uuid) AS players, count(*) AS joins FROM game
    WHERE started_at >= start_at AND started_at < stop_at
) g
CROSS JOIN LATERAL (
    SELECT count(DISTINCT (platform_user_id, attempt_id)) FILTER (WHERE type='LAUNCH_CLICK') AS clicks,
           count(DISTINCT (platform_user_id, attempt_id)) FILTER (WHERE type='LAUNCH_FAILED') AS failures
    FROM launcher_telemetry_event WHERE occurred_at >= start_at AND occurred_at < stop_at
) l;
