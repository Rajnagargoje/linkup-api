package com.linkup.user.notification;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

/** An FCM acceptance receipt, not a claim that the phone displayed the alert. */
@Entity @Getter @Setter
@Table(name = "push_receipts", indexes = @Index(name = "idx_push_receipt_created", columnList = "created_at"))
public class PushReceipt {
    @Id @Column(length = 96) private String id;
    @Column(nullable = false) private Instant createdAt = Instant.now();
}
