package com.linkup.user.entity;

import com.linkup.user.utils.Gender;
import com.linkup.user.utils.LookingFor;
import com.linkup.user.utils.Platform;
import com.linkup.user.utils.Role;
import com.linkup.user.utils.Status;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Public-facing identifier. Use THIS in URLs/DTOs sent to clients,
    // never the raw `id` — an incrementing PK lets anyone enumerate your
    // whole user base by walking /user/1, /user/2, ...
    @Column(unique = true, nullable = false, updatable = false)
    private String publicId;

    @Column(unique = true, nullable = false, length = 20)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(unique = true, nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // ================================
    // LOCATION
    // ================================

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    @Column(nullable = false)
    private Boolean locationVisible = true;

    @Column
    private LocalDateTime locationUpdatedAt;

    // Geohash prefix of (latitude, longitude) — lets "nearby people"
    // queries use an indexed range scan instead of a full-table haversine
    // scan over every user. Recompute this every time lat/long is saved.
    @Column(length = 12)
    private String geohash;

    // ================================
    // PROFILE
    // ================================

    // Real birth date. Compute `age` from this at read time instead of
    // trusting a client-supplied age that silently goes stale.
    @Column
    private LocalDate dob;

    @Column(name = "age")
    private Integer age;

    @Enumerated(EnumType.STRING)
    @Column
    private Gender gender;

    @Column(name = "profile_photo")
    private String profilePhoto;

    @ElementCollection
    @CollectionTable(name = "user_photos", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "photo_url")
    private List<String> photos = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "user_interests", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "interest")
    private List<String> interests = new ArrayList<>();

    @Column(name = "bio", length = 500)
    private String bio;

    @Column(nullable = false)
    private Boolean onboardingCompleted = false;

    // ================================
    // DATING / MATCHING PREFERENCES
    // ================================

    @Enumerated(EnumType.STRING)
    @Column
    private LookingFor lookingFor;

    @ElementCollection(targetClass = Gender.class)
    @CollectionTable(name = "user_gender_preference", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    private List<Gender> genderPreference = new ArrayList<>();

    @Column
    private Integer minAgePreference;

    @Column
    private Integer maxAgePreference;

    @Column
    private Double maxDistanceKm;

    // ================================
    // PRESENCE (real-time)
    // ================================

    // Driven by WebSocketEventListener on STOMP connect/disconnect, NOT
    // by trusting a client REST call — a client claiming "I'm online"
    // over plain HTTP proves nothing.
    @Column(name = "online", nullable = false)
    private Boolean online = false;

    @Column
    private LocalDateTime lastSeenAt;

    // ================================
    // TRUST & SAFETY
    // ================================

    @Column(nullable = false)
    private Boolean emailVerified = false;

    // Not wired up yet (phone/OTP is a later phase) — present now so the
    // shape is settled before that work starts, rather than bolting it
    // on and having to touch UserDTO/NearbyPersonResponse a second time.
    @Column(nullable = false)
    private Boolean phoneVerified = false;

    @Column(unique = true)
    private String phoneNumber;

    @Column(nullable = false)
    private Boolean isBanned = false;

    @Column
    private LocalDateTime bannedUntil;

    @Column(nullable = false)
    private Boolean isBlocked = false;

    @Column(nullable = false)
    private Integer reportCount = 0;

    // ================================
    // ACCOUNT LIFECYCLE
    // ================================

    @Column(nullable = false)
    private Boolean isActive = true;

    // Soft delete: keep the row (chat history references this user by
    // username) but strip PII and free the username/email up for reuse.
    @Column(nullable = false)
    private Boolean isDeleted = false;

    @Column
    private LocalDateTime deletedAt;

    // ================================
    // AUTH / SESSION SECURITY
    // ================================

    @Column
    private LocalDateTime lastLoginAt;

    @Column(nullable = false)
    private Integer failedLoginAttempts = 0;

    @Column
    private LocalDateTime lockedUntil;

    // Bumped on password change, "log out everywhere", or delete-account.
    // Embedded as a JWT claim and checked on every request — lets us
    // invalidate every outstanding token for a user without a blacklist
    // table, even though the JWT itself is otherwise stateless.
    @Column(nullable = false)
    private Integer tokenVersion = 0;

    // ================================
    // DEVICE / PUSH
    // ================================

    @Column
    private String fcmToken;

    @Enumerated(EnumType.STRING)
    @Column
    private Platform platform;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.publicId == null) {
            this.publicId = UUID.randomUUID().toString();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
