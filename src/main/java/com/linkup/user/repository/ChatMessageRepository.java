package com.linkup.user.repository;


import com.linkup.user.entity.chat.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Page<ChatMessage> findByConversationIdOrderByCreatedAtDesc(
            Long conversationId,
            Pageable pageable
    );

    @Query("""
        SELECT COUNT(m)
        FROM ChatMessage m
        WHERE m.conversation.id = :conversationId
          AND m.sender.publicId <> :currentUserPublicId
          AND (:lastReadMessageId IS NULL OR m.id > :lastReadMessageId)
          AND m.deletedForEveryone = false
    """)
    long countUnreadMessages(
            @Param("conversationId") Long conversationId,
            @Param("currentUserPublicId") String currentUserPublicId,
            @Param("lastReadMessageId") Long lastReadMessageId
    );
}