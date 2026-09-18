package com.linkup.user.entity;


import com.linkup.user.utils.ConnectionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "connections",
        indexes = {
                @Index(name = "idx_connection_sender", columnList = "sender_id"),
                @Index(name = "idx_connection_receiver", columnList = "receiver_id"),
                @Index(name = "idx_connection_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Connection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConnectionStatus status;

    @Column(name = "pair_key", nullable = false, unique = true, length = 100)
    private String pairKey;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime acceptedAt;

    @PrePersist
    protected void onCreate() {

        LocalDateTime now = LocalDateTime.now();

        this.createdAt = now;
        this.updatedAt = now;

        if (this.sender != null && this.receiver != null) {

            String senderId = this.sender.getPublicId();
            String receiverId = this.receiver.getPublicId();

            if (senderId.compareTo(receiverId) < 0) {
                this.pairKey = senderId + ":" + receiverId;
            } else {
                this.pairKey = receiverId + ":" + senderId;
            }
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}