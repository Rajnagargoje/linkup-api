package com.linkup.user.dto.chat;


import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

public record ConversationResponse(

        Long conversationId,

        String type,

        String friendPublicId,

        String friendUsername,

        String friendProfilePhoto,

        Integer friendAge,

        Boolean friendOnline,

        @JsonSerialize(using = ChatTimestampSerializer.class)
        LocalDateTime friendLastSeenAt,

        String lastMessage,

        @JsonSerialize(using = ChatTimestampSerializer.class)
        LocalDateTime lastMessageAt,

        long unreadCount,

        boolean muted,

        boolean archived,

        boolean pinned,
        boolean friends,
        int introductionsRemaining
) {
}
