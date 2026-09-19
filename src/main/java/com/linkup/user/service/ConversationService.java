package com.linkup.user.service;


import com.linkup.user.dto.chat.ConversationResponse;

import java.util.List;

public interface ConversationService {

    ConversationResponse getOrCreateDirectConversation(
            String currentUserPublicId,
            String friendPublicId
    );

    List<ConversationResponse> getMyConversations(
            String currentUserPublicId
    );

    void markAsRead(
            Long conversationId,
            String currentUserPublicId,
            Long messageId
    );

    void setMuted(
            Long conversationId,
            String currentUserPublicId,
            boolean muted
    );

    void setArchived(
            Long conversationId,
            String currentUserPublicId,
            boolean archived
    );

    void setPinned(
            Long conversationId,
            String currentUserPublicId,
            boolean pinned
    );
}