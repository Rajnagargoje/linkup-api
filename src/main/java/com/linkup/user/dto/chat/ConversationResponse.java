package com.linkup.user.dto.chat;


import java.time.LocalDateTime;

public record ConversationResponse(

        Long conversationId,

        String type,

        String friendPublicId,

        String friendUsername,

        String friendProfilePhoto,

        Integer friendAge,

        Boolean friendOnline,

        LocalDateTime friendLastSeenAt,

        String lastMessage,

        LocalDateTime lastMessageAt,

        long unreadCount,

        boolean muted,

        boolean archived,

        boolean pinned
) {
}