package com.linkup.user.auth;

import com.linkup.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Getter @Setter
@Table(name = "auth_sessions", indexes = {
    @Index(name = "idx_auth_session_user", columnList = "user_id"),
    @Index(name = "idx_auth_session_expiry", columnList = "expires_at")
})
public class AuthSession {
    @Id @Column(length = 36) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(nullable = false) private Integer tokenVersion;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant expiresAt;
    private Instant revokedAt;
}
