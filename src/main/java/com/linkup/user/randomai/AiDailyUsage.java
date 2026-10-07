package com.linkup.user.randomai;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Entity @Getter @Setter @Table(name = "ai_daily_usage")
public class AiDailyUsage {
    @Id @Column(length = 100) private String bucketKey;
    @Column(name = "usage_day", nullable = false) private LocalDate usageDay;
    @Column(nullable = false) private long requests;
    @Column(nullable = false) private long reservedMicros;
}
