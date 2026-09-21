package com.linkup.user.notification;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name = "push_devices", indexes = @Index(name = "idx_push_device_user", columnList = "userId")) @Getter @Setter
public class PushDevice {
    @Id @Column(length = 64) private String id;
    @Column(nullable = false) private String userId;
    @Column(nullable = false, length = 2048) private String token;
    @Column(nullable = false) private Integer sessionVersion;
    @Column(nullable = false) private Instant updatedAt = Instant.now();
}
