package dev.createserver.backend.account;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RegistrationSessionRepository extends JpaRepository<RegistrationSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RegistrationSession r where r.codeHash = :hash")
    Optional<RegistrationSession> lockByCodeHash(@Param("hash") String hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RegistrationSession r where r.verificationHash = :hash")
    Optional<RegistrationSession> lockByVerificationHash(@Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RegistrationSession r where r.id = :id")
    Optional<RegistrationSession> lockById(@Param("id") UUID id);
}
