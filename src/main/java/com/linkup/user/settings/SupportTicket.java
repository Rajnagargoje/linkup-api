package com.linkup.user.settings;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "support_tickets", indexes = {@Index(columnList = "owner,created_at")})
public class SupportTicket {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, length = 36) public String owner;
    @Column(nullable = false, length = 20) public String category;
    @Column(nullable = false, length = 120) public String subject;
    @Column(nullable = false, length = 2000) public String message;
    @Column(nullable = false, length = 20) public String status = "OPEN";
    @Column(length = 2000) public String reply;
    @Column(name = "created_at", nullable = false) public Instant createdAt = Instant.now();
    public Instant updatedAt;
}
