package com.linkup.user.auth;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;

public interface RefreshCredentialRepository extends JpaRepository<RefreshCredential, String> {
    // A scalar lookup avoids caching a stale credential before taking the session lock.
    @Query("select c.session.id from RefreshCredential c where c.tokenHash = :hash")
    Optional<String> sessionId(@Param("hash") String hash);
    @Modifying @Query("delete from RefreshCredential c where c.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}
