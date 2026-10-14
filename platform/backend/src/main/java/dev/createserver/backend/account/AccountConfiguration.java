package dev.createserver.backend.account;

import java.time.Clock;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.*;

@Configuration
@EnableConfigurationProperties(AccountSettings.class)
public class AccountConfiguration {
    @Bean Clock accountClock() { return Clock.systemUTC(); }
    @Bean PasswordEncoder accountPasswordEncoder() {
        return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(12)));
    }
}
