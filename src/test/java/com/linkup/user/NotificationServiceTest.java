package com.linkup.user;

import com.linkup.user.notification.*;
import com.linkup.user.entity.*;
import com.linkup.user.repository.*;
import com.linkup.user.service.ChatRelationshipPolicy;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    AppNotificationRepository repository = mock(AppNotificationRepository.class);
    NotificationPreferencesRepository preferences = mock(NotificationPreferencesRepository.class);
    PushDeviceRepository devices = mock(PushDeviceRepository.class);
    UserRepository users = mock(UserRepository.class);
    ChatRelationshipPolicy policy = mock(ChatRelationshipPolicy.class);
    SimpMessagingTemplate socket = mock(SimpMessagingTemplate.class);
    ConnectionRepository connections = mock(ConnectionRepository.class);
    ConversationParticipantRepository participants = mock(ConversationParticipantRepository.class);
    ChatMessageRepository messages = mock(ChatMessageRepository.class);
    NotificationService service = new NotificationService(repository, preferences, devices, users,
        participants, messages, policy, socket, connections);
    User recipient = user("recipient"), actor = user("actor");
    static User user(String name) {
        User u = new User(); u.setPublicId(name); u.setUsername(name); return u;
    }
    @BeforeEach void setup() {
        when(repository.save(any())).thenAnswer(call -> { AppNotification n = call.getArgument(0); n.setId(1L); return n; });
        when(users.findByUsername("recipient")).thenReturn(Optional.of(recipient));
    }
    @Test void durableRecordCreatedBeforeRealtimeAndRealtimeWaitsForCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.create(recipient, actor, NotificationType.FRIEND_REQUEST, 10L, null, "Request", "Hello", false);
            verify(repository).save(any()); verifyNoInteractions(socket);
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
            verify(socket).convertAndSendToUser(eq("recipient"), eq("/queue/notifications"), any(Object.class));
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
    @Test void blockedAndReportedActorsCannotCreateNotifications() {
        when(policy.hiddenFromDiscovery("u:recipient", "u:actor")).thenReturn(true);
        service.create(recipient, actor, NotificationType.MESSAGE, 10L, 11L, "Message", "Hello", false);
        verifyNoInteractions(repository, socket);
    }
    @Test void mutedMessagesStillHaveInboxEntryButNoPushJob() {
        service.create(recipient, actor, NotificationType.MESSAGE, 10L, 11L, "Message", "Hello", true);
        ArgumentCaptor<AppNotification> captured = ArgumentCaptor.forClass(AppNotification.class);
        verify(repository).save(captured.capture());
        assertTrue(captured.getValue().isPushDone());
        assertTrue(service.item(captured.getValue()).silent());
    }
    @Test void categoryPreferenceSuppressesPushWithoutLosingInboxRecord() {
        var prefs = new NotificationPreferences(); prefs.setMessages(false);
        when(preferences.findById("recipient")).thenReturn(Optional.of(prefs));
        service.create(recipient, actor, NotificationType.MESSAGE, 10L, 11L, "Message", "Hello", false);
        var captured = ArgumentCaptor.forClass(AppNotification.class); verify(repository).save(captured.capture());
        assertTrue(captured.getValue().isPushDone());
    }
    @Test void readsAreScopedToAuthenticatedRecipient() {
        assertThrows(IllegalArgumentException.class, () -> service.read("recipient", 999L));
        verify(repository).findByIdAndRecipient(999L, "recipient");
        verifyNoInteractions(socket);
    }
    @Test void bulkReadUsesVisibleWatermarkSoNewArrivalsStayUnread() {
        service.readAll("recipient", 42L);
        verify(repository).readAll(eq("recipient"), eq(42L), any());
    }
    @Test void tokenRegistrationUsesSessionOwnerAndLogoutDeletesOnlyTheirDevice() {
        service.register("recipient", "test-token");
        var captured = ArgumentCaptor.forClass(PushDevice.class); verify(devices).save(captured.capture());
        var device = captured.getValue();
        assertEquals("recipient", device.getUserId());
        assertEquals(64, device.getId().length());
        service.unregister("recipient", "test-token");
        verify(devices).deleteByIdAndUserId(device.getId(), "recipient");
    }
    @Test void rotatingDeviceTokenKeepsInstallationAndLoginOwnership() {
        service.register("recipient", "new-fcm-token", "installation", "login-session");
        var captured = ArgumentCaptor.forClass(PushDevice.class); verify(devices).save(captured.capture());
        assertEquals("login-session", captured.getValue().getAuthSessionId());
        assertEquals("installation", captured.getValue().getInstallationId());
        verify(devices).deleteByUserIdAndInstallationIdAndIdNot("recipient", "installation", captured.getValue().getId());
    }
    AppNotification notice(NotificationType type) {
        var n = new AppNotification(); n.setId(7L); n.setRecipient("recipient"); n.setType(type); n.setReferenceId(3L); n.setMessageId(4L); return n;
    }
    @Test void withdrawnFriendRequestsAndRemovedFriendsCannotAlert() {
        var n=notice(NotificationType.FRIEND_REQUEST);
        assertFalse(service.shouldAlert(n));
        var c=new Connection(); c.setReceiver(recipient); c.setSender(actor); c.setStatus(com.linkup.user.utils.ConnectionStatus.PENDING);
        when(connections.findById(3L)).thenReturn(Optional.of(c)); assertTrue(service.shouldAlert(n));
        c.setStatus(com.linkup.user.utils.ConnectionStatus.ACCEPTED); assertFalse(service.shouldAlert(n));
        n.setType(NotificationType.FRIEND_ACCEPTED); assertTrue(service.shouldAlert(n));
    }
    @Test void mutedReadAndDeletedMessagesCannotAlert() {
        var n=notice(NotificationType.MESSAGE);
        var p=new com.linkup.user.entity.chat.ConversationParticipant();
        var m=new com.linkup.user.entity.chat.ChatMessage();
        when(participants.findByConversationIdAndUserPublicId(3L,"recipient")).thenReturn(Optional.of(p));
        when(messages.findById(4L)).thenReturn(Optional.of(m));
        assertTrue(service.shouldAlert(n));
        p.setMuted(true); assertFalse(service.shouldAlert(n)); p.setMuted(false);
        p.setLastReadMessageId(4L); assertFalse(service.shouldAlert(n)); p.setLastReadMessageId(null);
        m.setDeletedForEveryone(true); assertFalse(service.shouldAlert(n));
    }
    @Test void aDelayedForegroundPushUsesTheCurrentConversationMuteState() {
        var n=notice(NotificationType.MESSAGE); var p=new com.linkup.user.entity.chat.ConversationParticipant(); p.setMuted(true);
        when(repository.findByIdAndRecipient(7L,"recipient")).thenReturn(Optional.of(n));
        when(participants.findByConversationIdAndUserPublicId(3L,"recipient")).thenReturn(Optional.of(p));
        when(messages.findById(4L)).thenReturn(Optional.of(new com.linkup.user.entity.chat.ChatMessage()));
        assertTrue(service.get("recipient",7L).silent());
    }
    @Test void trayReconciliationOnlyReturnsUnreadAlertsForTheSignedInOwner() {
        var own=notice(NotificationType.SYSTEM); var other=notice(NotificationType.SYSTEM); other.setId(8L); other.setRecipient("other");
        var read=notice(NotificationType.SYSTEM); read.setId(9L); read.setReadAt(java.time.Instant.now());
        when(repository.findAllById(List.of(7L,8L,9L))).thenReturn(List.of(own,other,read));
        assertEquals(List.of(7L),service.activeDeliveredIds("recipient",List.of(7L,8L,9L)));
    }
    @Test void supportPreferencesAreIndependentAndSynchronizeToOtherDevices() {
        var prefs=new NotificationPreferences(); prefs.setSupportReplies(false);
        when(preferences.findById("recipient")).thenReturn(Optional.of(prefs));
        assertFalse(service.shouldAlert(notice(NotificationType.SUPPORT_REPLY)));
        assertTrue(service.shouldAlert(notice(NotificationType.SYSTEM)));
        service.savePreferences("recipient",prefs);
        verify(socket).convertAndSendToUser(eq("recipient"),eq("/queue/notifications"),eq(Map.of("kind","PREFERENCES")));
    }
}
