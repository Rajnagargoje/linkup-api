package com.linkup.user.repository;



import com.linkup.user.entity.chat.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository
        extends JpaRepository<ChatMessage, Long> {

    Page<ChatMessage>
    findByConversationIdOrderByCreatedAtDesc(
            Long conversationId,
            Pageable pageable
    );
}