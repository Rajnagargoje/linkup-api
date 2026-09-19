package com.linkup.user.dto.chat;


public record TypingEvent(
        Long conversationId,
        boolean typing
) {
}