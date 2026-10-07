package com.linkup.user.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

/** Only hashes are stored, including the link to a rotated replacement. */
@Entity @Getter @Setter
@Table(name = "auth_refresh_credentials", indexes = {
    @Index(name = "idx_auth_refresh_session", columnList = "session_id"),
    @Index(name = "idx_auth_refresh_expiry", columnList = "expires_at")
})
public class RefreshCredential {
    @Id @Column(length = 64) private String tokenHash;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false) private AuthSession session;
    @Column(nullable = false) private Instant expiresAt;
    private Instant usedAt;
    @Column(length = 36) private String requestId;
    @Column(length = 64) private String nextHash;
}
