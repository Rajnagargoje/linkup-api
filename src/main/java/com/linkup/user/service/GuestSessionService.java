package com.linkup.user.service;

import com.linkup.user.entity.GuestSession;
import com.linkup.user.entity.User;
import com.linkup.user.repository.GuestSessionRepository;
import com.linkup.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service @RequiredArgsConstructor
public class GuestSessionService {
    public record GuestPrincipal(String id) implements Principal {
        @Override public String getName() { return "guest:" + id; }
    }
    public record Identity(String key, String displayName, String publicId, String username) {
        public boolean guest() { return publicId == null; }
    }
    public record GuestCredentials(String token, String displayName, Instant expiresAt) {}
    private final GuestSessionRepository guests;
    private final UserRepository users;
    private final ChatRelationshipPolicy policy;

    public GuestCredentials create(String name) {
        String normalized = name == null ? "" : name.strip();
        if (normalized.length() < 2 || normalized.length() > 30 || normalized.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Choose a display name with 2 to 30 characters.");
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        GuestSession guest = new GuestSession();
        guest.setId(UUID.randomUUID().toString()); guest.setTokenHash(hash(token)); guest.setDisplayName(normalized);
        guest.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS)); guests.save(guest);
        return new GuestCredentials(token, normalized, guest.getExpiresAt());
    }
    public GuestPrincipal authenticate(String token) {
        GuestSession guest = guests.findByTokenHash(hash(token)).orElseThrow(() -> new BadCredentialsException("Guest session expired. Start again."));
        checkExpiry(guest);
        return new GuestPrincipal(guest.getId());
    }
    public Identity resolve(Principal principal) {
        if (principal == null) throw new BadCredentialsException("Sign in or start a guest session.");
        if (principal instanceof GuestPrincipal p) {
            GuestSession guest = guests.findById(p.id()).orElseThrow(() -> new BadCredentialsException("Guest session expired."));
            checkExpiry(guest);
            if (guest.getAccountPublicId() == null) return new Identity("g:" + guest.getId(), guest.getDisplayName(), null, null);
            User user = users.findByPublicId(guest.getAccountPublicId()).orElseThrow(() -> new BadCredentialsException("Account unavailable."));
            if (!Objects.equals(user.getTokenVersion(), guest.getAccountTokenVersion())) throw new BadCredentialsException("Please sign in again.");
            return accountIdentity(user);
        }
        return accountIdentity(users.findByUsername(principal.getName()).orElseThrow(() -> new BadCredentialsException("Account unavailable.")));
    }
    @Transactional
    public GuestPrincipal claim(String token, String username) {
        GuestSession guest = guests.findForClaim(hash(token)).orElseThrow(() -> new IllegalArgumentException("Guest session expired."));
        checkExpiry(guest);
        Identity account = resolve(() -> username);
        if (guest.getAccountPublicId() != null && !guest.getAccountPublicId().equals(account.publicId()))
            throw new IllegalArgumentException("This guest session is already linked to another account.");
        User user = users.findByPublicId(account.publicId()).orElseThrow();
        guest.setAccountPublicId(account.publicId()); guest.setAccountTokenVersion(user.getTokenVersion());
        guests.save(guest);
        policy.migrateGuest("g:" + guest.getId(), account.key());
        return new GuestPrincipal(guest.getId());
    }
    private Identity accountIdentity(User user) {
        if (!Boolean.TRUE.equals(user.getIsActive()) || Boolean.TRUE.equals(user.getIsBanned()) || Boolean.TRUE.equals(user.getIsDeleted()))
            throw new BadCredentialsException("Account unavailable.");
        return new Identity("u:" + user.getPublicId(), user.getUsername(), user.getPublicId(), user.getUsername());
    }
    private void checkExpiry(GuestSession guest) {
        if (!guest.getExpiresAt().isAfter(Instant.now())) throw new BadCredentialsException("Guest session expired. Start again.");
    }
    private String hash(String token) {
        if (token == null || token.length() != 43) throw new BadCredentialsException("Invalid guest session.");
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
