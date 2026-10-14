package dev.createserver.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {
    public static void main(String[] args) {
        if (java.util.Arrays.stream(args).anyMatch(arg -> arg.startsWith("--generate-alpha-keys="))) {
            dev.createserver.backend.account.AlphaKeyCommand.run(args);
            return;
        }
        SpringApplication.run(BackendApplication.class, args);
    }
}
