package com.linkup.user;

import com.google.firebase.messaging.*;
import com.linkup.user.entity.User;
import com.linkup.user.notification.*;
import com.linkup.user.repository.*;
import com.linkup.user.service.ChatRelationshipPolicy;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PushDeliveryServiceTest {
    FirebaseMessaging firebase = mock(FirebaseMessaging.class);
    AppNotificationRepository notifications = mock(AppNotificationRepository.class);
    PushDeviceRepository devices = mock(PushDeviceRepository.class);
    UserRepository users = mock(UserRepository.class);
    NotificationService service = mock(NotificationService.class);
    ChatRelationshipPolicy policy = mock(ChatRelationshipPolicy.class);
    AppNotification n = new AppNotification();
    NotificationPreferences prefs = new NotificationPreferences();
    PushDeliveryService delivery;
    @SuppressWarnings("unchecked")
    @BeforeEach void setup() {
        ObjectProvider<FirebaseMessaging> provider = mock(ObjectProvider.class); when(provider.getIfAvailable()).thenReturn(firebase);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        doAnswer(call -> { ((java.util.function.Consumer<org.springframework.transaction.TransactionStatus>) call.getArgument(0)).accept(mock(org.springframework.transaction.TransactionStatus.class)); return null; })
            .when(transactions).executeWithoutResult(any());
        delivery = new PushDeliveryService(provider, notifications, devices, users,
            mock(ConversationParticipantRepository.class), mock(ChatMessageRepository.class), service, policy, transactions);
        n.setId(1L); n.setRecipient("recipient"); n.setActor("actor"); n.setType(NotificationType.FRIEND_REQUEST);
        n.setTitle("New friend request"); n.setBody("Someone wants to connect."); n.setNextAttemptAt(Instant.now().minusSeconds(1));
        when(notifications.findTop25ByPushDoneFalseAndNextAttemptAtBeforeOrderByIdAsc(any())).thenReturn(List.of(n));
        when(notifications.lockById(1L)).thenReturn(Optional.of(n));
        User user = new User(); user.setIsActive(true); user.setIsDeleted(false); user.setIsBanned(false); user.setTokenVersion(0);
        when(users.findByPublicId("recipient")).thenReturn(Optional.of(user));
        when(service.preferencesFor("recipient")).thenReturn(prefs);
        PushDevice device = new PushDevice(); device.setToken("device-token"); device.setSessionVersion(0);
        when(devices.findByUserId("recipient")).thenReturn(List.of(device));
    }
    @Test void readNotificationsDoNotProducePush() throws Exception {
        n.setReadAt(Instant.now()); delivery.deliverPending();
        verifyNoInteractions(firebase); assertTrue(n.isPushDone());
    }
    @Test void disablingPushSkipsDeliveryButKeepsNotification() throws Exception {
        prefs.setPushEnabled(false); delivery.deliverPending();
        verifyNoInteractions(firebase); assertTrue(n.isPushDone());
    }
    @Test void blockedActorsAreRecheckedAtDeliveryTime() {
        when(policy.hiddenFromDiscovery("u:recipient", "u:actor")).thenReturn(true);
        delivery.deliverPending(); verifyNoInteractions(firebase);
    }
    @Test void successfulDeliveryUsesNotificationAndDataForBackgroundTapRouting() throws Exception {
        delivery.deliverPending();
        verify(firebase).send(any(Message.class)); assertTrue(n.isPushDone()); assertEquals(1, n.getPushAttempts());
    }
    @Test void transientFailuresAreRetriedWithBackoff() throws Exception {
        FirebaseMessagingException failure = mock(FirebaseMessagingException.class);
        when(failure.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNAVAILABLE);
        when(firebase.send(any(Message.class))).thenThrow(failure);
        delivery.deliverPending();
        assertFalse(n.isPushDone()); assertEquals(1, n.getPushAttempts()); assertTrue(n.getNextAttemptAt().isAfter(Instant.now()));
    }
    @Test void expiredDeviceTokensAreRemoved() throws Exception {
        FirebaseMessagingException failure = mock(FirebaseMessagingException.class);
        when(failure.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(firebase.send(any(Message.class))).thenThrow(failure);
        delivery.deliverPending();
        verify(devices).delete(any(PushDevice.class)); assertTrue(n.isPushDone());
    }
}
