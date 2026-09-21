package com.linkup.user.notification;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name = "app_notifications", indexes = {
    @Index(name = "idx_notification_inbox", columnList = "recipient,createdAt"),
    @Index(name = "idx_notification_push", columnList = "pushDone,nextAttemptAt")
}) @Getter @Setter
public class AppNotification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String recipient;
    private String actor;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private NotificationType type;
    private Long referenceId;
    private Long messageId;
    @Column(nullable = false, length = 160) private String title;
    @Column(nullable = false, length = 500) private String body;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    private Instant readAt;
    @Column(nullable = false) private boolean pushDone;
    @Column(nullable = false) private int pushAttempts;
    @Column(nullable = false) private Instant nextAttemptAt = Instant.now().plusSeconds(5);
}
