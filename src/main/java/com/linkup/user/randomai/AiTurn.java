package com.linkup.user.randomai;

import java.time.Instant;
import java.util.UUID;

public record AiTurn(String id, String senderId, String sender, String content, String timeStamp, String clientId) {
    public static AiTurn create(String senderId, String sender, String content, String clientId) {
        return new AiTurn(UUID.randomUUID().toString(), senderId, sender, content, Instant.now().toString(), clientId);
    }
    public boolean assistant() { return senderId != null && senderId.startsWith("ai:"); }
}
