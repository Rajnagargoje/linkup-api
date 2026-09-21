package com.linkup.user.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
@Entity @Table(name = "chat_reports") @Getter @Setter
public class ChatReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String reporter;
    @Column(nullable = false) private String target;
    @Column(nullable = false) private String matchId;
    @Column(nullable = false, length = 500) private String reason;
    @Column(columnDefinition = "text") private String evidence;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    @Column(nullable = false) private boolean reviewed;
}
