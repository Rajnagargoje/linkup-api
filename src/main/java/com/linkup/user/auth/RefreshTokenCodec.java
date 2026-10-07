package com.linkup.user.auth;

import io.jsonwebtoken.io.Decoders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class RefreshTokenCodec {
    private final SecureRandom random = new SecureRandom();
    private final byte[] rotationKey;

    public RefreshTokenCodec(@Value("${jwt.secret}") String secret) {
        rotationKey = hmac(Decoders.BASE64.decode(secret), "linkup:refresh-rotation:v1");
    }
    public String create() {
        byte[] value = new byte[32]; random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
    public String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    // Reproduces a replacement for a bounded retry of the SAME refresh attempt.
    // Neither the raw old token nor the raw replacement is kept in the database.
    public String replacement(String token, String requestId) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(rotationKey, token + "\n" + requestId));
    }
    private static byte[] hmac(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException impossible) { throw new IllegalStateException(impossible); }
    }
}
