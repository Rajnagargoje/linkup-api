package com.linkup.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "guest_sessions")
@Getter @Setter
public class GuestSession {
    @Id private String id;
    @Column(nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(nullable = false, length = 30) private String displayName;
    @Column(nullable = false) private Instant expiresAt;
    private String accountPublicId;
    private Integer accountTokenVersion;
}
