package dev.createserver.backend.bridge;
import org.springframework.boot.test.context.SpringBootTest;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.datasource.url=jdbc:h2:mem:bridge;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver","platform.game.server-id=test-world","platform.game.service-secret=test-only-game-service-independent-secret-32","platform.accounts.bot-backend-secret=test-only-bot-service-secret-at-least-32","REPORT_ADMIN_TELEGRAM_IDS=111"})
class BridgeTest extends BridgeContract {}
