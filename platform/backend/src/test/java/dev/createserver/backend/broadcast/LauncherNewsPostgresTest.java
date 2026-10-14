package dev.createserver.backend.broadcast;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
@Tag("postgres")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class LauncherNewsPostgresTest extends LauncherNewsContract {}
