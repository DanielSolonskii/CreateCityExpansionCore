package dev.createserver.backend.bridge;
import org.springframework.boot.test.context.SpringBootTest;import org.junit.jupiter.api.Tag;
@Tag("postgres")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"platform.game.server-id=test-world","platform.game.service-secret=test-only-game-service-independent-secret-32","platform.accounts.bot-backend-secret=test-only-bot-service-secret-at-least-32","REPORT_ADMIN_TELEGRAM_IDS=111"})
class BridgePostgresTest extends BridgeContract {}
