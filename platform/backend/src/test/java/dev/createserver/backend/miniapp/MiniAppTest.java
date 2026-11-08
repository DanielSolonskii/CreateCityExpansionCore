package dev.createserver.backend.miniapp;
import org.springframework.boot.test.context.SpringBootTest;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"MINI_APP_BOT_TOKEN=test-only-miniapp-key","GAME_SERVER_ID=mini-test","spring.datasource.url=jdbc:h2:mem:miniapp;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver"})
class MiniAppTest extends MiniAppContract{}
