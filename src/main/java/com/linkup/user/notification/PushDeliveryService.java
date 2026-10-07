package com.linkup.user.notification;

import com.google.firebase.messaging.*;
import com.linkup.user.auth.AuthSessionService;
import com.linkup.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Service @RequiredArgsConstructor @Slf4j
public class PushDeliveryService {
    private final ObjectProvider<FirebaseMessaging> firebase;
    private final AppNotificationRepository notifications;
    private final PushDeviceRepository devices;
    private final UserRepository users;
    private final NotificationService service;
    private final TransactionTemplate transactions;
    private final PushReceiptRepository receipts;
    private final AuthSessionService sessions;

    @Scheduled(fixedDelayString = "${linkup.push.poll-ms:2000}")
    public void deliverPending() {
        var sender = firebase.getIfAvailable();
        if (sender == null) return;
        for (var candidate : notifications.findTop25ByPushDoneFalseAndNextAttemptAtBeforeOrderByNextAttemptAtAscIdAsc(Instant.now())) {
            try { transactions.executeWithoutResult(status -> notifications.lockById(candidate.getId()).ifPresent(n -> deliver(sender, n))); }
            catch (RuntimeException failure) { log.warn("Push job {} will be retried.", candidate.getId()); }
        }
    }

    private void deliver(FirebaseMessaging sender, AppNotification n) {
        if (n.isPushDone() || n.getNextAttemptAt().isAfter(Instant.now())) return;
        var owner = users.findByPublicId(n.getRecipient()).orElse(null);
        if (n.getCreatedAt().isBefore(Instant.now().minus(1, ChronoUnit.DAYS))
            || owner == null || !Boolean.TRUE.equals(owner.getIsActive()) || Boolean.TRUE.equals(owner.getIsDeleted())
            || Boolean.TRUE.equals(owner.getIsBanned()) || !service.shouldAlert(n)) {
            n.setPushDone(true); return;
        }
        var prefs = service.preferencesFor(n.getRecipient());
        boolean retry = false;
        boolean registered = false;
        for (var device : devices.findByUserId(n.getRecipient())) {
            if (!Objects.equals(device.getSessionVersion(), owner.getTokenVersion())
                || device.getUpdatedAt().isBefore(Instant.now().minus(60, ChronoUnit.DAYS))
                || (device.getAuthSessionId() != null && !sessions.isActive(device.getAuthSessionId(), owner.getUsername()))) {
                devices.delete(device); continue;
            }
            String receiptId = n.getId() + ":" + device.getId();
            if (receipts.existsById(receiptId)) { registered = true; continue; }
            try {
                sender.send(message(n, device, prefs));
                registered = true;
                var receipt = new PushReceipt(); receipt.setId(receiptId); receipts.save(receipt);
            } catch (FirebaseMessagingException error) {
                if (error.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) devices.delete(device);
                else { registered = true; retry = true; log.warn("Push job {} failed: {}", n.getId(), error.getMessagingErrorCode()); }
            }
        }
        // Keep jobs during registration outages; the durable inbox is authoritative.
        if (!registered) { n.setNextAttemptAt(Instant.now().plusSeconds(60)); return; }
        n.setPushAttempts(n.getPushAttempts() + 1);
        n.setPushDone(!retry || n.getPushAttempts() >= 5);
        n.setNextAttemptAt(Instant.now().plusSeconds(Math.min(3600, 30L << n.getPushAttempts())));
    }

    private Message message(AppNotification n, PushDevice device, NotificationPreferences prefs) {
        String channel = n.getType() == NotificationType.MESSAGE ? "linkup_messages"
            : n.getType() == NotificationType.FRIEND_REQUEST || n.getType() == NotificationType.FRIEND_ACCEPTED ? "linkup_friends" : "linkup_updates";
        boolean preview = n.getType() != NotificationType.MESSAGE || prefs.isMessagePreview();
        // Android's delivered-notification API does not preserve FCM custom data.
        // The tag lets the client remove precisely the messages it has read.
        String tag = "linkup:v1:" + n.getRecipient() + ":" + n.getType() + ":"
            + Objects.toString(n.getReferenceId(), "") + ":" + Objects.toString(n.getMessageId(), "") + ":" + n.getId();
        return Message.builder().setToken(device.getToken())
            .setNotification(Notification.builder().setTitle(preview ? n.getTitle() : "New message")
                .setBody(preview ? n.getBody() : "Open LinkUp to read your message.").build())
            .putData("notificationId", n.getId().toString()).putData("recipientId", n.getRecipient())
            .putData("messageId", Objects.toString(n.getMessageId(), ""))
            .putData("type", n.getType().name()).putData("referenceId", Objects.toString(n.getReferenceId(), ""))
            .setAndroidConfig(AndroidConfig.builder()
                .setPriority(n.getType() == NotificationType.SYSTEM ? AndroidConfig.Priority.NORMAL : AndroidConfig.Priority.HIGH)
                .setTtl(3600000).setNotification(AndroidNotification.builder().setChannelId(channel)
                    .setIcon("ic_stat_linkup").setColor("#667eea").setTag(tag)
                    .setDefaultSound(true).setDefaultVibrateTimings(true)
                    .setVisibility(AndroidNotification.Visibility.PRIVATE).setNotificationCount(1).build()).build())
            .build();
    }

    @Scheduled(initialDelay = 3600000, fixedDelay = 86400000)
    public void cleanReceipts() {
        transactions.executeWithoutResult(status -> receipts.deleteOlderThan(Instant.now().minus(2, ChronoUnit.DAYS)));
    }
}
