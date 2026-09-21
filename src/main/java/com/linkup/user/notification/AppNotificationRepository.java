package com.linkup.user.notification;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;

public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {
    String VISIBLE = " n.recipient = :owner and (n.actor is null or (" +
        "not exists (select b.id from ChatBlock b where (b.blocker = concat('u:', :owner) and b.target = concat('u:', n.actor)) or (b.target = concat('u:', :owner) and b.blocker = concat('u:', n.actor))) " +
        "and not exists (select r.id from ChatReport r where r.reporter = concat('u:', :owner) and r.target = concat('u:', n.actor)))) ";
    @Query("select n from AppNotification n where" + VISIBLE + "and (:unread = false or n.readAt is null) order by n.createdAt desc, n.id desc")
    Page<AppNotification> inbox(String owner, boolean unread, Pageable page);
    @Query("select count(n) from AppNotification n where" + VISIBLE + "and n.readAt is null")
    long unreadCount(String owner);
    @Query("select count(n) from AppNotification n where" + VISIBLE + "and n.readAt is null and n.type = :type")
    long unreadType(String owner, NotificationType type);
    Optional<AppNotification> findByIdAndRecipient(Long id, String recipient);
    @Modifying @Query("update AppNotification n set n.readAt = :now where n.recipient = :owner and n.readAt is null and n.id <= :throughId")
    void readAll(String owner, Long throughId, Instant now);
    @Modifying @Query("update AppNotification n set n.readAt = :now where n.recipient = :owner and n.type = :type and n.referenceId = :reference and n.readAt is null")
    void readReference(String owner, NotificationType type, Long reference, Instant now);
    @Modifying @Query("update AppNotification n set n.readAt = :now where n.recipient = :owner and n.type = com.linkup.user.notification.NotificationType.MESSAGE and n.referenceId = :conversation and n.messageId <= :throughMessage and n.readAt is null")
    void readMessages(String owner, Long conversation, Long throughMessage, Instant now);
    List<AppNotification> findTop25ByPushDoneFalseAndNextAttemptAtBeforeOrderByIdAsc(Instant now);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select n from AppNotification n where n.id = :id")
    Optional<AppNotification> lockById(Long id);
}
