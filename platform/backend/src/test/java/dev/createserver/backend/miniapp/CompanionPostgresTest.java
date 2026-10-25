package dev.createserver.backend.miniapp;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
@Tag("postgres") @SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"MINI_APP_BOT_TOKEN=test-only-miniapp-key","GAME_SERVER_ID=mini-test","CCEC_BACKEND_SECRET=test-only-game-service-independent-secret-32","REPORT_ADMIN_TELEGRAM_IDS=111"})
class CompanionPostgresTest extends CompanionContract{}
