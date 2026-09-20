package com.linkup.user.repository;


import com.linkup.user.entity.chat.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    // Count across all conversations, including deleted messages, so the allowance cannot be reset.
    @Query("select count(m) from ChatMessage m where m.sender.publicId = :sender and m.conversation.type = com.linkup.user.utils.ConversationType.DIRECT and exists (select p.id from ConversationParticipant p where p.conversation = m.conversation and p.user.publicId = :recipient)")
    long countBetween(String sender, String recipient);

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
