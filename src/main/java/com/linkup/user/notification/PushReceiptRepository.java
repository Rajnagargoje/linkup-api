package com.linkup.user.notification;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

public interface PushReceiptRepository extends JpaRepository<PushReceipt, String> {
    @Modifying @Query("delete from PushReceipt r where r.createdAt < :before")
    void deleteOlderThan(@Param("before") Instant before);
}
