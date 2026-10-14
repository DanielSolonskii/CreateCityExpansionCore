package dev.createserver.backend.broadcast;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public projection of explicitly confirmed broadcasts. Never expose admin/delivery data. */
@Service
public class LauncherNews {
    private static final Pattern RELEASE = Pattern.compile(
        "^(?:#update|обновление)\\s+v?(\\d{1,5}\\.\\d{1,5}(?:\\.\\d{1,5}){1,2}(?:-[A-Za-z0-9.-]{1,32})?)\\s*$",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    public record Item(UUID id, String title, String body, String releaseVersion, Instant publishedAt, boolean hasPhoto) {}
    private final JdbcTemplate db;
    public LauncherNews(JdbcTemplate db) { this.db = db; }

    static Item project(UUID id, String text, Instant publishedAt, boolean photo) {
        String normalized = text.replace("\r\n", "\n").strip();
        String[] lines = normalized.split("\n", 2);
        var match = RELEASE.matcher(lines[0].strip());
        String version = match.matches() ? match.group(1) : null;
        String body = version == null ? normalized : (lines.length == 2 ? lines[1].strip() : "");
        String title = version == null ? normalized.lines().findFirst().orElse("Новости RE:CREATED") : "Обновление " + version;
        if (title.isBlank()) title = "Новости RE:CREATED";
        // Text is rendered as text, not HTML/Markdown. Marker is presentation only.
        return new Item(id, title.substring(0, Math.min(120, title.length())), body, version, publishedAt, photo);
    }

    @Transactional(readOnly = true)
    public List<Item> list() {
        return db.query("SELECT id,text,confirmed_at,photo_file_id FROM broadcast_post " +
            "WHERE status IN ('QUEUED','COMPLETED') AND confirmed_at IS NOT NULL " +
            "ORDER BY confirmed_at DESC,id DESC LIMIT 50", (rs, n) -> project(
                rs.getObject("id", UUID.class), rs.getString("text"), rs.getTimestamp("confirmed_at").toInstant(),
                rs.getString("photo_file_id") != null));
    }
}
