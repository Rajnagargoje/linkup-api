package com.linkup.user.notification;

import com.linkup.user.entity.User;
import com.linkup.user.repository.*;
import com.linkup.user.service.ChatRelationshipPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class NotificationService {
    private final AppNotificationRepository notifications;
    private final NotificationPreferencesRepository preferences;
    private final PushDeviceRepository devices;
    private final UserRepository users;
    private final ConversationParticipantRepository participants;
    private final ChatMessageRepository messages;
    private final ChatRelationshipPolicy policy;
    private final SimpMessagingTemplate realtime;

    public record Item(Long id, NotificationType type, String title, String body, String actorId,
                       Long referenceId, Long messageId, Instant createdAt, Instant readAt) {}
    public record Counts(long total, long requests, long messages) {}
    public Item item(AppNotification n) {
        return new Item(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getActor(),
            n.getReferenceId(), n.getMessageId(), n.getCreatedAt(), n.getReadAt());
    }
    public User user(String username) { return users.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("User unavailable.")); }
    public NotificationPreferences preferencesFor(String publicId) {
        return preferences.findById(publicId).orElseGet(() -> {
            var p = new NotificationPreferences(); p.setUserId(publicId); return p;
        });
    }
    public void create(User recipient, User actor, NotificationType type, Long reference, Long messageId,
                       String title, String body, boolean muted) {
        if (actor != null && (recipient.getPublicId().equals(actor.getPublicId())
            || policy.hiddenFromDiscovery("u:" + recipient.getPublicId(), "u:" + actor.getPublicId()))) return;
        var n = new AppNotification();
        n.setRecipient(recipient.getPublicId()); n.setActor(actor == null ? null : actor.getPublicId());
        n.setType(type); n.setReferenceId(reference); n.setMessageId(messageId);
        n.setTitle(title.substring(0, Math.min(title.length(), 160)));
        n.setBody(body.substring(0, Math.min(body.length(), 500)));
        var prefs = preferencesFor(recipient.getPublicId());
        n.setPushDone(muted || !prefs.isPushEnabled() || !prefs.allows(type));
        notifications.save(n);
        publishAfterCommit(recipient.getUsername(), Map.of("kind", "NEW", "notification", item(n), "silent", muted || !prefs.allows(type)));
    }
    public void reportReceived(String identity, Long reportId) {
        if (!identity.startsWith("u:")) return;
        users.findByPublicId(identity.substring(2)).ifPresent(owner -> create(owner, null, NotificationType.REPORT_UPDATE,
            reportId, null, "Report received", "Your report was submitted for review. You can find updates here.", false));
    }
    public void publishAfterCommit(String username, Object event) {
        Runnable send = () -> {
            try { realtime.convertAndSendToUser(username, "/queue/notifications", event); }
            catch (RuntimeException ignored) { /* The durable inbox is reconciled on reconnect. */ }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { send.run(); }
            });
        else send.run();
    }
    public void changed(String username) { publishAfterCommit(username, Map.of("kind", "CHANGED")); }
    @Transactional(readOnly = true)
    public Page<Item> list(String username, int page, boolean unread) {
        return notifications.inbox(user(username).getPublicId(), unread, PageRequest.of(Math.max(0, page), 30)).map(this::item);
    }
    @Transactional(readOnly = true)
    public Counts counts(String username) {
        var owner = user(username);
        long unreadMessages = 0;
        for (var participant : participants.findByUserPublicIdOrderByConversationUpdatedAtDesc(owner.getPublicId())) {
            if (participant.isArchived()) continue;
            boolean hidden = participant.getConversation().getParticipants().stream()
                .map(p -> p.getUser().getPublicId()).filter(id -> !id.equals(owner.getPublicId()))
                .anyMatch(id -> policy.hiddenFromDiscovery("u:" + owner.getPublicId(), "u:" + id));
            if (!hidden) unreadMessages += messages.countUnreadMessages(participant.getConversation().getId(), owner.getPublicId(), participant.getLastReadMessageId());
        }
        return new Counts(notifications.unreadCount(owner.getPublicId()),
            notifications.unreadType(owner.getPublicId(), NotificationType.FRIEND_REQUEST), unreadMessages);
    }
    public void read(String username, Long id) {
        var owner = user(username);
        var n = notifications.findByIdAndRecipient(id, owner.getPublicId())
            .orElseThrow(() -> new IllegalArgumentException("Notification not found."));
        if (n.getReadAt() == null) n.setReadAt(Instant.now());
        changed(username);
    }
    @Transactional(readOnly = true)
    public Item get(String username, Long id) {
        var owner = user(username);
        var n = notifications.findByIdAndRecipient(id, owner.getPublicId())
            .orElseThrow(() -> new IllegalArgumentException("Notification not found."));
        if (n.getActor() != null && policy.hiddenFromDiscovery("u:" + owner.getPublicId(), "u:" + n.getActor()))
            throw new IllegalArgumentException("Notification unavailable.");
        return item(n);
    }
    public void readAll(String username, Long throughId) {
        notifications.readAll(user(username).getPublicId(), throughId, Instant.now()); changed(username);
    }
    public void readRequests(String username, List<Long> connectionIds) {
        var owner = user(username);
        for (Long id : connectionIds) notifications.readReference(owner.getPublicId(), NotificationType.FRIEND_REQUEST, id, Instant.now());
        changed(username);
    }
    public void readMessages(User owner, Long conversation, Long throughMessage) {
        notifications.readMessages(owner.getPublicId(), conversation, throughMessage, Instant.now()); changed(owner.getUsername());
    }
    public NotificationPreferences savePreferences(String username, NotificationPreferences input) {
        input.setUserId(user(username).getPublicId());
        return preferences.save(input);
    }
    private String deviceId(String token) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public void register(String username, String token) {
        var owner = user(username);
        String hash = deviceId(token);
        var device = devices.findById(hash).orElseGet(PushDevice::new);
        device.setId(hash); device.setUserId(owner.getPublicId()); device.setToken(token);
        device.setSessionVersion(owner.getTokenVersion()); device.setUpdatedAt(Instant.now()); devices.save(device);
    }
    public void unregister(String username, String token) {
        String hash = deviceId(token);
        devices.deleteByIdAndUserId(hash, user(username).getPublicId());
    }
}
