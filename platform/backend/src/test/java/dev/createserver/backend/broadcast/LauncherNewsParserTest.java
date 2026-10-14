package dev.createserver.backend.broadcast;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class LauncherNewsParserTest {
    LauncherNews.Item parse(String text) { return LauncherNews.project(UUID.randomUUID(),text,Instant.EPOCH,false); }
    @Test void explicitMarkerAndLegacyHeading() {
        for (String first : new String[]{"#update 1.0.18","Обновление 1.0.18","ОБНОВЛЕНИЕ v1.0.18"}) {
            var p=parse(first+"\r\n\r\n• Добавлены моды\n• Исправления");
            assertThat(p.releaseVersion()).isEqualTo("1.0.18");
            assertThat(p.title()).isEqualTo("Обновление 1.0.18");
            assertThat(p.body()).startsWith("• Добавлены");
        }
    }
    @Test void noAccidentalVersionExtractionOrHtmlExecution() {
        for(String text:new String[]{"Сегодня обновление 1.0.18","#update 1.0.18 ссылка","#update 1.0","Новости\n#update 1.0.18"})
            assertThat(parse(text).releaseVersion()).isNull();
        assertThat(parse("<script>alert(1)</script>").body()).isEqualTo("<script>alert(1)</script>");
        assertThat(parse("").title()).isEqualTo("Новости RE:CREATED");
    }
}
