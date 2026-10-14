package dev.createserver.backend.account;

import dev.createserver.backend.BackendApplication;
import java.time.Instant;
import org.springframework.boot.*;
import org.springframework.boot.builder.SpringApplicationBuilder;

public final class AlphaKeyCommand {
    private AlphaKeyCommand() {}
    public static void run(String[] args) {
        int count = 0;
        Instant expiry = null;
        for (String arg : args) {
            if (arg.startsWith("--generate-alpha-keys=") && count == 0) {
                count = Integer.parseInt(arg.substring("--generate-alpha-keys=".length()));
            } else if (arg.startsWith("--alpha-keys-expires-at=") && expiry == null) {
                expiry = Instant.parse(arg.substring("--alpha-keys-expires-at=".length()));
            } else throw new IllegalArgumentException("Unsupported alpha key command argument");
        }
        if (count < 1 || count > 10000 || (expiry != null && !expiry.isAfter(Instant.now()))) {
            throw new IllegalArgumentException("Count must be 1..10000 and expiry must be in the future");
        }
        try (var context = new SpringApplicationBuilder(BackendApplication.class)
                .web(WebApplicationType.NONE).bannerMode(Banner.Mode.OFF).logStartupInfo(false).run()) {
            var generated = context.getBean(AlphaKeyService.class).generate(count, expiry);
            // Explicit one-time administrative output, never a logger or a retrieval endpoint.
            for (var key : generated) System.out.println(key.id() + "\t" + key.plaintext());
        }
    }
}
