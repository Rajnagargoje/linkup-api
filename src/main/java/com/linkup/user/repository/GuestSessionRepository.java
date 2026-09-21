package com.linkup.user.repository;

import com.linkup.user.entity.GuestSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface GuestSessionRepository extends JpaRepository<GuestSession, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GuestSession g where g.tokenHash = :hash")
    Optional<GuestSession> findForClaim(String hash);
    Optional<GuestSession> findByTokenHash(String hash);
}
