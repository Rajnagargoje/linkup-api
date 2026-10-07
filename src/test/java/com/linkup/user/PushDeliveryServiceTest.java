package com.linkup.user;

import com.google.firebase.messaging.*;
import com.linkup.user.auth.AuthSessionService;
import com.linkup.user.entity.User;
import com.linkup.user.notification.*;
import com.linkup.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PushDeliveryServiceTest {
    FirebaseMessaging firebase = mock(FirebaseMessaging.class);
    AppNotificationRepository notifications = mock(AppNotificationRepository.class);
    PushDeviceRepository devices = mock(PushDeviceRepository.class);
    UserRepository users = mock(UserRepository.class);
    NotificationService service = mock(NotificationService.class);
    PushReceiptRepository receipts = mock(PushReceiptRepository.class);
    AuthSessionService sessions = mock(AuthSessionService.class);
    Set<String> accepted = new HashSet<>();
    AppNotification n = new AppNotification();
    NotificationPreferences prefs = new NotificationPreferences();
    PushDeliveryService delivery;
    PushDevice device;
    @SuppressWarnings("unchecked")
    @BeforeEach void setup() {
        ObjectProvider<FirebaseMessaging> provider = mock(ObjectProvider.class); when(provider.getIfAvailable()).thenReturn(firebase);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        doAnswer(call -> { ((java.util.function.Consumer<org.springframework.transaction.TransactionStatus>) call.getArgument(0)).accept(mock(org.springframework.transaction.TransactionStatus.class)); return null; })
            .when(transactions).executeWithoutResult(any());
        delivery = new PushDeliveryService(provider, notifications, devices, users, service, transactions, receipts, sessions);
        n.setId(1L); n.setRecipient("recipient"); n.setActor("actor"); n.setType(NotificationType.FRIEND_REQUEST);
        n.setReferenceId(7L); n.setTitle("A new update"); n.setBody("Private message text"); n.setNextAttemptAt(Instant.now().minusSeconds(1));
        when(notifications.findTop25ByPushDoneFalseAndNextAttemptAtBeforeOrderByNextAttemptAtAscIdAsc(any())).thenReturn(List.of(n));
        when(notifications.lockById(1L)).thenReturn(Optional.of(n)); when(notifications.unreadCount("recipient")).thenReturn(4L);
        User user = new User(); user.setUsername("recipient"); user.setIsActive(true); user.setIsDeleted(false); user.setIsBanned(false); user.setTokenVersion(0);
        when(users.findByPublicId("recipient")).thenReturn(Optional.of(user));
        when(service.preferencesFor("recipient")).thenReturn(prefs); when(service.shouldAlert(n)).thenReturn(true);
        device = device("one"); when(devices.findByUserId("recipient")).thenReturn(List.of(device));
        when(receipts.existsById(anyString())).thenAnswer(call -> accepted.contains(call.getArgument(0)));
        when(receipts.save(any())).thenAnswer(call -> { PushReceipt receipt=call.getArgument(0); accepted.add(receipt.getId()); return receipt; });
    }
    PushDevice device(String id) {
        PushDevice d=new PushDevice(); d.setId(id); d.setUserId("recipient"); d.setToken("token-"+id); d.setSessionVersion(0); return d;
    }
    FirebaseMessagingException failure(MessagingErrorCode code) {
        try {
            var constructor=FirebaseMessagingException.class.getDeclaredConstructor(com.google.firebase.ErrorCode.class,
                String.class,Throwable.class,com.google.firebase.IncomingHttpResponse.class,MessagingErrorCode.class);
            constructor.setAccessible(true);
            return constructor.newInstance(com.google.firebase.ErrorCode.INTERNAL,"Test FCM error",null,null,code);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    @Test void currentReadMuteBlockAndPreferenceStateSuppressesPush() {
        when(service.shouldAlert(n)).thenReturn(false); delivery.deliverPending();
        verifyNoInteractions(firebase); assertTrue(n.isPushDone());
    }
    @ParameterizedTest @EnumSource(NotificationType.class)
    void eachNotificationTypeCarriesTapMetadataAndAStableTrayTag(NotificationType type) throws Exception {
        n.setType(type); delivery.deliverPending();
        var sent=ArgumentCaptor.forClass(Message.class); verify(firebase).send(sent.capture());
        @SuppressWarnings("unchecked") var data=(Map<String,String>)ReflectionTestUtils.getField(sent.getValue(),"data");
        assertEquals(type.name(),data.get("type")); assertEquals("recipient",data.get("recipientId")); assertEquals("1",data.get("notificationId"));
        var android=ReflectionTestUtils.getField(sent.getValue(),"androidConfig");
        var notification=ReflectionTestUtils.getField(android,"notification");
        assertEquals("linkup:v1:recipient:"+type.name()+":7::1",ReflectionTestUtils.getField(notification,"tag"));
        assertEquals(1,ReflectionTestUtils.getField(notification,"notificationCount"));
        assertTrue(n.isPushDone()); assertTrue(accepted.contains("1:one"));
    }
    @Test void hiddenMessagePreviewsNeverLeakTextOrSenderInThePush() throws Exception {
        n.setType(NotificationType.MESSAGE); delivery.deliverPending();
        var sent=ArgumentCaptor.forClass(Message.class); verify(firebase).send(sent.capture());
        var notification=ReflectionTestUtils.getField(sent.getValue(),"notification");
        assertEquals("New message",ReflectionTestUtils.getField(notification,"title"));
        assertEquals("Open LinkUp to read your message.",ReflectionTestUtils.getField(notification,"body"));
    }
    @Test void transientFailuresAreRetriedWithBackoff() throws Exception {
        when(firebase.send(any(Message.class))).thenThrow(failure(MessagingErrorCode.UNAVAILABLE));
        delivery.deliverPending();
        assertFalse(n.isPushDone()); assertEquals(1,n.getPushAttempts()); assertTrue(n.getNextAttemptAt().isAfter(Instant.now()));
    }
    @Test void successfulDevicesAreNotSentAgainWhenAnotherDeviceNeedsRetry() throws Exception {
        var second=device("two"); when(devices.findByUserId("recipient")).thenReturn(List.of(device,second));
        when(firebase.send(any(Message.class))).thenReturn("accepted").thenThrow(failure(MessagingErrorCode.UNAVAILABLE));
        delivery.deliverPending(); assertEquals(Set.of("1:one"),accepted); assertFalse(n.isPushDone());
        clearInvocations(firebase); when(firebase.send(any(Message.class))).thenReturn("accepted"); n.setNextAttemptAt(Instant.now().minusSeconds(1));
        delivery.deliverPending();
        var sent=ArgumentCaptor.forClass(Message.class); verify(firebase,times(1)).send(sent.capture());
        assertEquals("token-two",ReflectionTestUtils.getField(sent.getValue(),"token")); assertTrue(n.isPushDone());
    }
    @Test void noDeviceYetKeepsJobAvailableForRegistrationRecovery() {
        when(devices.findByUserId("recipient")).thenReturn(List.of()); delivery.deliverPending();
        assertFalse(n.isPushDone()); assertEquals(0,n.getPushAttempts()); verifyNoInteractions(firebase);
    }
    @Test void revokedLoginCannotReceiveAnotherPush() {
        device.setAuthSessionId("revoked"); when(sessions.isActive("revoked","recipient")).thenReturn(false);
        delivery.deliverPending(); verify(devices).delete(device); verifyNoInteractions(firebase);
    }
    @Test void activeLoginCanReceivePushWithoutAnOpenWebsocket() throws Exception {
        device.setAuthSessionId("active"); when(sessions.isActive("active","recipient")).thenReturn(true);
        delivery.deliverPending(); verify(firebase).send(any(Message.class));
    }
    @Test void expiredFcmTokensAreRemovedAndJobWaitsForAReplacement() throws Exception {
        when(firebase.send(any(Message.class))).thenThrow(failure(MessagingErrorCode.UNREGISTERED));
        delivery.deliverPending(); verify(devices).delete(device); assertFalse(n.isPushDone());
    }
    @Test void staleNotificationsExpireInsteadOfAlertingMuchLater() {
        n.setCreatedAt(Instant.now().minusSeconds(90000)); delivery.deliverPending();
        assertTrue(n.isPushDone()); verifyNoInteractions(firebase);
    }
}
