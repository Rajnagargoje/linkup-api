package com.linkup.user.notification;

import com.google.firebase.messaging.*;
import com.linkup.user.repository.*;
import com.linkup.user.service.ChatRelationshipPolicy;
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
    private final ConversationParticipantRepository participants;
    private final ChatMessageRepository messages;
    private final NotificationService service;
    private final ChatRelationshipPolicy policy;
    private final TransactionTemplate transactions;

    @Scheduled(fixedDelayString = "${linkup.push.poll-ms:5000}")
    public void deliverPending() {
        var sender = firebase.getIfAvailable();
        if (sender == null) return;
        for (var candidate : notifications.findTop25ByPushDoneFalseAndNextAttemptAtBeforeOrderByIdAsc(Instant.now())) {
            try { transactions.executeWithoutResult(status -> notifications.lockById(candidate.getId()).ifPresent(n -> deliver(sender, n))); }
            catch (RuntimeException failure) { log.warn("Push job {} will be retried.", candidate.getId()); }
        }
    }

    private void deliver(FirebaseMessaging sender, AppNotification n) {
        if (n.isPushDone() || n.getNextAttemptAt().isAfter(Instant.now())) return;
        var owner = users.findByPublicId(n.getRecipient()).orElse(null);
        var prefs = service.preferencesFor(n.getRecipient());
        boolean skip = n.getReadAt() != null || n.getCreatedAt().isBefore(Instant.now().minus(1, ChronoUnit.DAYS))
            || owner == null || !Boolean.TRUE.equals(owner.getIsActive()) || Boolean.TRUE.equals(owner.getIsDeleted())
            || Boolean.TRUE.equals(owner.getIsBanned()) || !prefs.isPushEnabled() || !prefs.allows(n.getType())
            || (n.getActor() != null && policy.hiddenFromDiscovery("u:" + n.getRecipient(), "u:" + n.getActor()));
        if (n.getType() == NotificationType.MESSAGE) {
            var participant = participants.findByConversationIdAndUserPublicId(n.getReferenceId(), n.getRecipient()).orElse(null);
            var message = messages.findById(n.getMessageId()).orElse(null);
            skip |= participant == null || participant.isMuted() || participant.isArchived()
                || (participant.getLastReadMessageId() != null && participant.getLastReadMessageId() >= n.getMessageId())
                || message == null || message.isDeletedForEveryone();
        }
        if (skip) { n.setPushDone(true); return; }
        boolean retry = false;
        for (var device : devices.findByUserId(n.getRecipient())) {
            if (!Objects.equals(device.getSessionVersion(), owner.getTokenVersion())
                || device.getUpdatedAt().isBefore(Instant.now().minus(60, ChronoUnit.DAYS))) {
                devices.delete(device); continue;
            }
            try {
                String channel = n.getType() == NotificationType.MESSAGE ? "linkup_messages"
                    : n.getType() == NotificationType.FRIEND_REQUEST || n.getType() == NotificationType.FRIEND_ACCEPTED ? "linkup_friends" : "linkup_updates";
                boolean preview = n.getType() != NotificationType.MESSAGE || prefs.isMessagePreview();
                sender.send(Message.builder().setToken(device.getToken())
                    .setNotification(Notification.builder().setTitle(preview ? n.getTitle() : "New message")
                        .setBody(preview ? n.getBody() : "Open LinkUp to read your message.").build())
                    .putData("notificationId", n.getId().toString()).putData("recipientId", n.getRecipient())
                    .putData("messageId", Objects.toString(n.getMessageId(), ""))
                    .putData("type", n.getType().name()).putData("referenceId", Objects.toString(n.getReferenceId(), ""))
                    .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH)
                        .setTtl(3600000).setNotification(AndroidNotification.builder().setChannelId(channel)
                            .setIcon("ic_stat_linkup").setColor("#667eea").setTag("linkup-" + n.getId()).build()).build())
                    .build());
            } catch (FirebaseMessagingException error) {
                if (error.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) devices.delete(device);
                else { retry = true; log.warn("Push job {} failed: {}", n.getId(), error.getMessagingErrorCode()); }
            }
        }
        n.setPushAttempts(n.getPushAttempts() + 1);
        n.setPushDone(!retry || n.getPushAttempts() >= 5);
        n.setNextAttemptAt(Instant.now().plusSeconds(Math.min(3600, 30L << n.getPushAttempts())));
    }
}
