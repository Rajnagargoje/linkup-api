package com.linkup.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "chat_blocks", indexes = {@Index(columnList = "blocker,target")})
@Getter @Setter
public class ChatBlock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String blocker;
    @Column(nullable = false) private String target;
    @Column(nullable = false) private Instant createdAt = Instant.now();
}
