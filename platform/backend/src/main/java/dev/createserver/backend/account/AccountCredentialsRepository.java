package dev.createserver.backend.account;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountCredentialsRepository extends JpaRepository<AccountCredentials, UUID> {
    Optional<AccountCredentials> findByUsername(String username);
}
