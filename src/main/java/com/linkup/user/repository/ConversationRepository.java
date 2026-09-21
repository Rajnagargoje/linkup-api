package com.linkup.user.repository;



import com.linkup.user.entity.chat.Conversation;
import com.linkup.user.utils.ConversationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ConversationRepository
        extends JpaRepository<Conversation, Long> {

    @Query("""
        SELECT DISTINCT c
        FROM Conversation c
        JOIN c.participants p1
        JOIN c.participants p2
        WHERE c.type = :type
          AND p1.user.publicId = :user1
          AND p2.user.publicId = :user2
    """)
    Optional<Conversation> findDirectConversation(
            String user1,
            String user2,
            ConversationType type
    );
}