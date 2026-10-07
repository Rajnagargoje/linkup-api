package com.linkup.user.auth;

import com.linkup.user.entity.User;
import com.linkup.user.exception.CustomAuthException;
import com.linkup.user.mapper.UserMapper;
import com.linkup.user.repository.UserRepository;
import com.linkup.user.service.impl.JWTService;
import com.linkup.user.service.impl.UserPrinciples;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class AuthSessionService {
    private final AuthSessionRepository sessions;
    private final RefreshCredentialRepository credentials;
    private final UserRepository users;
    private final JWTService jwt;
    private final UserMapper mapper;
    private final RefreshTokenCodec codec;
    private final Duration idleLifetime;

    public AuthSessionService(AuthSessionRepository sessions, RefreshCredentialRepository credentials,
            UserRepository users, JWTService jwt, UserMapper mapper, RefreshTokenCodec codec,
            @Value("${LINKUP_REFRESH_IDLE_DAYS:30}") int idleDays) {
        if (idleDays < 1 || idleDays > 90) throw new IllegalArgumentException("LINKUP_REFRESH_IDLE_DAYS must be 1–90");
        this.sessions = sessions; this.credentials = credentials; this.users = users;
        this.jwt = jwt; this.mapper = mapper; this.codec = codec; this.idleLifetime = Duration.ofDays(idleDays);
    }

    @Transactional
    public AuthSessionResponse issue(String username) {
        User user = users.findByUsername(username).orElseThrow(this::expired);
        if (!enabled(user)) throw expired();
        Instant now = Instant.now();
        AuthSession session = new AuthSession();
        session.setId(UUID.randomUUID().toString()); session.setUser(user);
        session.setTokenVersion(user.getTokenVersion()); session.setCreatedAt(now);
        session.setExpiresAt(now.plus(idleLifetime)); sessions.save(session);
        String token = codec.create();
        addCredential(token, session);
        return response(session, token);
    }

    @Transactional(noRollbackFor = CustomAuthException.class)
    public AuthSessionResponse refresh(String token, String requestId) {
        String hash = codec.hash(token);
        String id = credentials.sessionId(hash).orElseThrow(this::expired);
        AuthSession session = sessions.lockById(id).orElseThrow(this::expired);
        RefreshCredential credential = credentials.findById(hash).orElseThrow(this::expired);
        Instant now = Instant.now();
        if (!active(session, now) || !credential.getExpiresAt().isAfter(now)) {
            session.setRevokedAt(now); throw expired();
        }
        if (credential.getUsedAt() != null) {
            // A dropped HTTP response can safely be retried with the persisted request ID.
            if (Objects.equals(requestId, credential.getRequestId())
                    && credential.getUsedAt().plusSeconds(120).isAfter(now)) {
                String replacement = codec.replacement(token, requestId);
                RefreshCredential next = credentials.findById(codec.hash(replacement)).orElse(null);
                if (next != null && next.getUsedAt() == null && next.getExpiresAt().isAfter(now))
                    return response(session, replacement);
            }
            // Reuse of an already rotated token revokes this device's entire session.
            session.setRevokedAt(now); throw expired();
        }
        String replacement = codec.replacement(token, requestId);
        credential.setUsedAt(now); credential.setRequestId(requestId);
        credential.setNextHash(codec.hash(replacement));
        session.setExpiresAt(now.plus(idleLifetime));
        addCredential(replacement, session);
        return response(session, replacement);
    }

    @Transactional
    public Optional<String> revoke(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return Optional.empty();
        return credentials.sessionId(codec.hash(token)).flatMap(sessions::lockById).map(session -> {
            session.setRevokedAt(Instant.now()); return session.getUser().getUsername();
        });
    }

    @Transactional(readOnly = true)
    public boolean isActive(String id, String username) {
        return sessions.findById(id).filter(session -> username.equals(session.getUser().getUsername())
                && active(session, Instant.now())).isPresent();
    }

    @Scheduled(initialDelay = 3600000, fixedDelay = 86400000)
    @Transactional
    public void cleanExpired() {
        Instant now = Instant.now(); credentials.deleteExpired(now); sessions.deleteExpired(now);
    }

    private void addCredential(String token, AuthSession session) {
        RefreshCredential credential = new RefreshCredential();
        credential.setTokenHash(codec.hash(token)); credential.setSession(session);
        credential.setExpiresAt(session.getExpiresAt()); credentials.save(credential);
    }
    private AuthSessionResponse response(AuthSession session, String refreshToken) {
        User user = session.getUser();
        return new AuthSessionResponse(jwt.generateToken(user.getUsername(), user.getTokenVersion(), session.getId()),
                refreshToken, session.getExpiresAt(), mapper.toUserDTO(user));
    }
    private boolean active(AuthSession session, Instant now) {
        return session.getRevokedAt() == null && session.getExpiresAt().isAfter(now)
                && Objects.equals(session.getTokenVersion(), session.getUser().getTokenVersion()) && enabled(session.getUser());
    }
    private boolean enabled(User user) {
        UserPrinciples principal = new UserPrinciples(user);
        return principal.isEnabled() && principal.isAccountNonLocked();
    }
    private CustomAuthException expired() { return new CustomAuthException("Your sign-in has expired. Please log in again."); }
}
