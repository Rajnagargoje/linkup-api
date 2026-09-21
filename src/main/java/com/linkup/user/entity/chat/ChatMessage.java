package com.linkup.user.entity.chat;


import com.linkup.user.entity.User;
import com.linkup.user.utils.MessageStatus;
import com.linkup.user.utils.MessageType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "messages",
        indexes = {
                @Index(
                        name = "idx_message_conversation_created",
                        columnList = "conversation_id,created_at"
                ),
                @Index(
                        name = "idx_message_sender",
                        columnList = "sender_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

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
            name = "sender_id",
            nullable = false
    )
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MessageType type;

    @Column(length = 5000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_message_id")
    private ChatMessage replyToMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime editedAt;

    private LocalDateTime deletedAt;

    @Column(nullable = false)
    private boolean deletedForEveryone = false;

    private LocalDateTime deliveredAt;

    private LocalDateTime readAt;

    @PrePersist
    protected void onCreate() {

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (status == null) {
            status = MessageStatus.SENT;
        }
    }
}