package dev.createserver.backend.miniapp;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
@Tag("postgres") @SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"MINI_APP_BOT_TOKEN=test-only-miniapp-key","GAME_SERVER_ID=mini-test"})
class MiniAppPostgresTest extends MiniAppContract{}
