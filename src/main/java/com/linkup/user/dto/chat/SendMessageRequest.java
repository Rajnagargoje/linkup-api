package com.linkup.user.dto.chat;



import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(

        @NotNull
        Long conversationId,

        @Size(max = 5000)
        String content,

        Long replyToMessageId
) {
}