package com.linkup.user.auth;

import com.linkup.user.entity.User;
import com.linkup.user.exception.CustomAuthException;
import com.linkup.user.mapper.UserMapper;
import com.linkup.user.repository.UserRepository;
import com.linkup.user.service.impl.JWTService;
import com.linkup.user.utils.Role;
import com.linkup.user.utils.Status;
import org.junit.jupiter.api.*;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.show-sql=false", "logging.level.org.hibernate.SQL=OFF", "spring.cloud.config.enabled=false",
    "jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTA=", "jwt.expiration-ms=1800000"
})
@Import({AuthSessionService.class, RefreshTokenCodec.class, JWTService.class, AuthSessionServiceTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthSessionServiceTest {
    @TestConfiguration static class Config {
        @Bean UserMapper mapper() { return Mappers.getMapper(UserMapper.class); }
    }
    @Autowired AuthSessionService service;
    @Autowired AuthSessionRepository sessions;
    @Autowired RefreshCredentialRepository credentials;
    @Autowired UserRepository users;
    @Autowired JWTService jwt;
    @Autowired RefreshTokenCodec codec;
    User user;
    @BeforeEach void setup() {
        user = new User(); user.setUsername("u" + UUID.randomUUID().toString().substring(0, 10));
        user.setEmail(user.getUsername() + "@example.test"); user.setPassword("unused");
        user.setRole(Role.USER); user.setStatus(Status.OFFLINE); user.setIsBanned(false);
        user = users.saveAndFlush(user);
    }
    String id() { return UUID.randomUUID().toString(); }
    String sessionId(AuthSessionResponse response) { return jwt.extractSessionId(response.token()); }

    @Test void issuesPerDeviceSessionsAndStoresOnlyCredentialHashes() {
        var a = service.issue(user.getUsername()); var b = service.issue(user.getUsername());
        assertNotEquals(sessionId(a), sessionId(b));
        assertTrue(service.isActive(sessionId(a), user.getUsername()));
        assertFalse(service.isActive(sessionId(a), "someone-else"));
        assertTrue(credentials.existsById(codec.hash(a.refreshToken())));
        assertFalse(credentials.existsById(a.refreshToken()));
        assertTrue(a.refreshExpiresAt().isAfter(Instant.now().plusSeconds(29 * 86400)));
    }
    @Test void rotatesAndSupportsOneIdempotentHttpRetry() {
        var original = service.issue(user.getUsername()); String request = id();
        var rotated = service.refresh(original.refreshToken(), request);
        var retry = service.refresh(original.refreshToken(), request);
        assertNotEquals(original.refreshToken(), rotated.refreshToken());
        assertEquals(rotated.refreshToken(), retry.refreshToken());
        assertEquals(sessionId(original), sessionId(rotated));
        assertNotNull(credentials.findById(codec.hash(original.refreshToken())).orElseThrow().getUsedAt());
        assertNotNull(service.refresh(rotated.refreshToken(), id()));
    }
    @Test void replayRevocationCommitsEvenThoughRefreshThrows() {
        var original = service.issue(user.getUsername());
        var rotated = service.refresh(original.refreshToken(), id());
        assertThrows(CustomAuthException.class, () -> service.refresh(original.refreshToken(), id()));
        assertFalse(service.isActive(sessionId(original), user.getUsername()));
        assertThrows(CustomAuthException.class, () -> service.refresh(rotated.refreshToken(), id()));
        assertNotNull(sessions.findById(sessionId(original)).orElseThrow().getRevokedAt());
    }
    @Test void logoutRevokesOnlyTheSelectedDeviceAndAcceptsItsPreviouslyUsedToken() {
        var a = service.issue(user.getUsername()); var b = service.issue(user.getUsername());
        var rotated = service.refresh(a.refreshToken(), id());
        service.revoke(a.refreshToken());
        assertFalse(service.isActive(sessionId(rotated), user.getUsername()));
        assertTrue(service.isActive(sessionId(b), user.getUsername()));
        assertThrows(CustomAuthException.class, () -> service.refresh(rotated.refreshToken(), id()));
        assertTrue(service.revoke("not-a-token").isEmpty());
    }
    @Test void inactiveExpiredSessionCannotBeRenewed() {
        var original = service.issue(user.getUsername());
        var session = sessions.findById(sessionId(original)).orElseThrow();
        session.setExpiresAt(Instant.now().minusSeconds(1)); sessions.saveAndFlush(session);
        assertThrows(CustomAuthException.class, () -> service.refresh(original.refreshToken(), id()));
    }
    @Test void changedAccountVersionRevokesRenewal() {
        var original = service.issue(user.getUsername());
        user.setTokenVersion(1); users.saveAndFlush(user);
        assertFalse(service.isActive(sessionId(original), user.getUsername()));
        assertThrows(CustomAuthException.class, () -> service.refresh(original.refreshToken(), id()));
    }
    @Test void disabledOrLockedAccountCannotRenew() {
        var original = service.issue(user.getUsername());
        user.setIsBanned(true); users.saveAndFlush(user);
        assertThrows(CustomAuthException.class, () -> service.refresh(original.refreshToken(), id()));
        assertThrows(CustomAuthException.class, () -> service.issue(user.getUsername()));
    }
    @Test void unrelatedRandomCredentialCannotRevokeAValidSession() {
        var original = service.issue(user.getUsername());
        assertThrows(CustomAuthException.class, () -> service.refresh(codec.create(), id()));
        assertTrue(service.isActive(sessionId(original), user.getUsername()));
    }
    @Test void expiredRetryWindowRejectsOldCredential() {
        var original = service.issue(user.getUsername()); String request = id();
        service.refresh(original.refreshToken(), request);
        var used = credentials.findById(codec.hash(original.refreshToken())).orElseThrow();
        used.setUsedAt(Instant.now().minusSeconds(121)); credentials.saveAndFlush(used);
        assertThrows(CustomAuthException.class, () -> service.refresh(original.refreshToken(), request));
    }
    @Test void concurrentIdenticalRetriesReturnTheSameReplacement() throws Exception {
        var original = service.issue(user.getUsername()); String request = id();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var gate = new CountDownLatch(1);
            Callable<AuthSessionResponse> call = () -> { gate.await(); return service.refresh(original.refreshToken(), request); };
            var first = pool.submit(call); var second = pool.submit(call); gate.countDown();
            assertEquals(first.get(10, TimeUnit.SECONDS).refreshToken(), second.get(10, TimeUnit.SECONDS).refreshToken());
            assertTrue(service.isActive(sessionId(original), user.getUsername()));
        } finally { pool.shutdownNow(); }
    }
    @Test void cleanupRemovesExpiredCredentialsBeforeTheirSession() {
        var original = service.issue(user.getUsername());
        var credential = credentials.findById(codec.hash(original.refreshToken())).orElseThrow();
        credential.setExpiresAt(Instant.now().minusSeconds(10)); credentials.saveAndFlush(credential);
        var session = sessions.findById(sessionId(original)).orElseThrow();
        session.setExpiresAt(Instant.now().minusSeconds(10)); sessions.saveAndFlush(session);
        service.cleanExpired();
        assertFalse(sessions.existsById(sessionId(original)));
        assertFalse(credentials.existsById(codec.hash(original.refreshToken())));
    }
}
