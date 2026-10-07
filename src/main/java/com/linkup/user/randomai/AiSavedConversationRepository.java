package com.linkup.user.randomai;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface AiSavedConversationRepository extends JpaRepository<AiSavedConversation, String> {
    Optional<AiSavedConversation> findByOwnerPublicIdAndPersona(String ownerPublicId, String persona);
    List<AiSavedConversation> findByOwnerPublicIdOrderByUpdatedAtDesc(String ownerPublicId);
    void deleteByOwnerPublicIdAndPersona(String ownerPublicId, String persona);
    void deleteByOwnerPublicId(String ownerPublicId);
}
