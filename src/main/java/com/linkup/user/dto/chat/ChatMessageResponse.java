package com.linkup.user.dto.chat;


import java.time.LocalDateTime;

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

        LocalDateTime createdAt,

        LocalDateTime editedAt,

        LocalDateTime deliveredAt,

        LocalDateTime readAt,

        boolean deletedForEveryone
) {
}