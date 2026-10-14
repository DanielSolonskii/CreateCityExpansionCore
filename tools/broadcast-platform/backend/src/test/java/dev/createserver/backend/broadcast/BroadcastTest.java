package dev.createserver.backend.broadcast;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
@ActiveProfiles("test")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.datasource.url=jdbc:h2:mem:broadcasts;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver","platform.accounts.bot-backend-secret=test-only-bot-service-secret-at-least-32","REPORT_ADMIN_TELEGRAM_IDS=111,222"})
class BroadcastTest extends BroadcastContract {}
