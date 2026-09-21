package com.linkup.user.repository;




import com.linkup.user.entity.chat.ConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationParticipantRepository
        extends JpaRepository<ConversationParticipant, Long> {

    Optional<ConversationParticipant>
    findByConversationIdAndUserPublicId(
            Long conversationId,
            String publicId
    );

    boolean existsByConversationIdAndUserPublicId(
            Long conversationId,
            String publicId
    );

    List<ConversationParticipant>
    findByUserPublicIdOrderByConversationUpdatedAtDesc(
            String publicId
    );
}