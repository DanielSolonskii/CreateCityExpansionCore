package dev.createserver.backend.account;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AlphaAccessKeyRepository extends JpaRepository<AlphaAccessKey, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from AlphaAccessKey k where k.keyHash = :hash")
    Optional<AlphaAccessKey> lockByHash(@Param("hash") String hash);
}
