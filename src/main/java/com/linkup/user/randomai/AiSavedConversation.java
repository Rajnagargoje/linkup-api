package com.linkup.user.randomai;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Getter @Setter
@Table(name = "ai_saved_conversations", uniqueConstraints = @UniqueConstraint(columnNames = {"owner_public_id", "persona"}))
public class AiSavedConversation {
    @Id @Column(length = 36) private String id;
    @Column(name = "owner_public_id", nullable = false, length = 36) private String ownerPublicId;
    @Column(nullable = false, length = 30) private String persona;
    @Column(name = "history_json", nullable = false, columnDefinition = "text") private String historyJson;
    @Column(nullable = false) private Instant updatedAt;
}
