package com.linkup.user.entity.chat;


import com.linkup.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "conversation_participants",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_conversation_user",
                        columnNames = {
                                "conversation_id",
                                "user_id"
                        }
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "conversation_id",
            nullable = false
    )
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private User user;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    private Long lastReadMessageId;

    @Column(nullable = false)
    private boolean muted = false;

    @Column(nullable = false)
    private boolean archived = false;

    @Column(nullable = false)
    private boolean pinned = false;

    @PrePersist
    protected void onCreate() {

        if (joinedAt == null) {
            joinedAt = LocalDateTime.now();
        }
    }
}