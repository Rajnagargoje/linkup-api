package com.linkup.user.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSession s where s.id = :id")
    Optional<AuthSession> lockById(@Param("id") String id);
    @Modifying @Query("delete from AuthSession s where s.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}
