package com.linkup.user.service.impl;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JWTService {

    // Was hardcoded in source (and committed to git) before — now read
    // from config so it can live in an env var / secrets manager instead.
    // Generate a real one for prod, e.g.: openssl rand -base64 64
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms:1800000}") // 30 minutes by default
    private long expirationMs;

    public String generateToken(String username, Integer tokenVersion) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tokenVersion", tokenVersion);
        return createToken(claims, username);
    }

    private String createToken(Map<String, Object> claims, String username) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSignKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private Key getSignKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Returns null instead of throwing on a malformed/expired/tampered
     * token. Callers (JwtAuthFilter, the STOMP interceptor) treat a null
     * result as "not authenticated" and fall through to a clean 401,
     * instead of the request blowing up as an unhandled 500.
     */
    public String extractUsername(String token) {
        Claims claims = extractAllClaimsOrNull(token);
        return claims == null ? null : claims.getSubject();
    }

    public Integer extractTokenVersion(String token) {
        Claims claims = extractAllClaimsOrNull(token);
        if (claims == null) return null;
        Object v = claims.get("tokenVersion");
        return v == null ? null : Integer.valueOf(v.toString());
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaimsOrNull(token);
        return claims == null ? null : claimsResolver.apply(claims);
    }

    private Claims extractAllClaimsOrNull(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSignKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException | IllegalArgumentException ex) {
            // Expired, malformed, bad signature, or null/blank token —
            // all treated the same way: "this token isn't usable".
            return null;
        }
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public boolean isTokenExpired(String token) {
        Date expiration = extractExpiration(token);
        return expiration == null || expiration.before(new Date());
    }

    /**
     * Full validation: signature valid, not expired, username matches,
     * account is enabled/unlocked, AND the token's tokenVersion still
     * matches the account's current tokenVersion. That last check is
     * what lets us kill every outstanding token for a user immediately
     * on ban, delete-account, or "log out everywhere" — otherwise a JWT
     * would stay valid until its natural expiry no matter what happens
     * to the account server-side.
     */
    public boolean validateToken(String token, UserPrinciples userDetails) {
        String username = extractUsername(token);
        if (username == null || !username.equals(userDetails.getUsername())) return false;
        if (isTokenExpired(token)) return false;
        if (!userDetails.isEnabled() || !userDetails.isAccountNonLocked()) return false;

        Integer tokenVersion = extractTokenVersion(token);
        return tokenVersion != null && tokenVersion.equals(userDetails.getTokenVersion());
    }
}
