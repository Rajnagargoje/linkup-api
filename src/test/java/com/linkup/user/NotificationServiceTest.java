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
    NotificationService service = new NotificationService(repository, preferences, devices, users,
        mock(ConversationParticipantRepository.class), mock(ChatMessageRepository.class), policy, socket);
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
}
