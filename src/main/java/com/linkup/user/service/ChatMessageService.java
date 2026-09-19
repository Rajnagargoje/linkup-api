package com.linkup.user.service;


import com.linkup.user.dto.chat.ChatMessageResponse;
import com.linkup.user.dto.chat.SendMessageRequest;

import java.util.List;

public interface ChatMessageService {

    ChatMessageResponse sendMessage(
            String username,
            SendMessageRequest request
    );

    List<ChatMessageResponse> getMessages(
            String username,
            Long conversationId,
            int page,
            int size
    );

    ChatMessageResponse editMessage(
            String username,
            Long messageId,
            String content
    );

    void deleteForEveryone(
            String username,
            Long messageId
    );

    void markDelivered(
            String username,
            Long messageId
    );

    void markRead(
            String username,
            Long messageId
    );
}