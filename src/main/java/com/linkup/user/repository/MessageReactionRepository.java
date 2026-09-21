package com.linkup.user.repository;


import com.linkup.user.entity.chat.MessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MessageReactionRepository
        extends JpaRepository<MessageReaction, Long> {

    Optional<MessageReaction>
    findByMessageIdAndUserPublicId(
            Long messageId,
            String publicId
    );
}