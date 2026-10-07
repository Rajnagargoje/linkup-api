package com.linkup.user.dto.chat;


import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

public record ChatMessageResponse(

        Long id,

        Long conversationId,

        String senderPublicId,

        String senderUsername,

        String senderProfilePhoto,

        String type,

        String content,

        String status,

        Long replyToMessageId,

        String replyToContent,

        @JsonSerialize(using = ChatTimestampSerializer.class)
        LocalDateTime createdAt,

        @JsonSerialize(using = ChatTimestampSerializer.class)
        LocalDateTime editedAt,

        @JsonSerialize(using = ChatTimestampSerializer.class)
        LocalDateTime deliveredAt,

        @JsonSerialize(using = ChatTimestampSerializer.class)
        LocalDateTime readAt,

        boolean deletedForEveryone
) {
}