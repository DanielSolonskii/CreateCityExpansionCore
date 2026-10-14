package dev.createserver.backend.account;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("postgres")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "platform.accounts.telegram-bot-username=create_test_bot",
        "platform.accounts.bot-backend-secret=test-only-service-secret-at-least-32-characters"
})
class AccountPostgresTest extends AccountDatabaseContract {}
