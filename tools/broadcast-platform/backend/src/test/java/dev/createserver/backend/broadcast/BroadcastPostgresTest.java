package dev.createserver.backend.broadcast;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
@Tag("postgres")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"platform.accounts.bot-backend-secret=test-only-bot-service-secret-at-least-32","REPORT_ADMIN_TELEGRAM_IDS=111,222"})
class BroadcastPostgresTest extends BroadcastContract {}
