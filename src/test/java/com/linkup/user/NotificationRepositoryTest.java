package com.linkup.user;

import com.linkup.user.notification.*;
import com.linkup.user.entity.*;
import com.linkup.user.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.cloud.config.enabled=false"
})
class NotificationRepositoryTest {
    @Autowired AppNotificationRepository notifications;
    @Autowired ChatBlockRepository blocks;
    @Autowired ChatReportRepository reports;
    @Autowired PushReceiptRepository receipts;
    @Autowired PushDeviceRepository devices;
    @Autowired com.linkup.user.settings.SupportTicketRepository tickets;

    AppNotification notification(String owner, String actor, NotificationType type, long message) {
        AppNotification n = new AppNotification(); n.setRecipient(owner); n.setActor(actor); n.setType(type);
        n.setTitle("Update"); n.setBody("Body"); n.setReferenceId(9L); n.setMessageId(message);
        return notifications.saveAndFlush(n);
    }
    @Test void inboxAndCountsRespectBlocksReportsAndOwnership() {
        notification("me", "blocked", NotificationType.MESSAGE, 1);
        notification("me", "reported", NotificationType.FRIEND_REQUEST, 2);
        notification("me", null, NotificationType.SYSTEM, 3);
        notification("other", null, NotificationType.SYSTEM, 4);
        ChatBlock block = new ChatBlock(); block.setBlocker("u:blocked"); block.setTarget("u:me"); blocks.saveAndFlush(block);
        ChatReport report = new ChatReport(); report.setReporter("u:me"); report.setTarget("u:reported");
        report.setMatchId("match"); report.setReason("Spam"); reports.saveAndFlush(report);
        assertEquals(1, notifications.unreadCount("me"));
        assertEquals(0, notifications.unreadType("me", NotificationType.FRIEND_REQUEST));
        assertEquals(NotificationType.SYSTEM, notifications.inbox("me", false, PageRequest.of(0, 30)).getContent().get(0).getType());
    }
    @Test void readingConversationStopsAtTheReadMessageAndDoesNotReadOtherUsers() {
        notification("me", "friend", NotificationType.MESSAGE, 10);
        notification("me", "friend", NotificationType.MESSAGE, 11);
        notification("other", "friend", NotificationType.MESSAGE, 10);
        notifications.readMessages("me", 9L, 10L, Instant.now());
        assertEquals(1, notifications.unreadCount("me"));
        assertEquals(1, notifications.unreadCount("other"));
    }
    @Test void markingAllDoesNotConsumeNewerNotifications() {
        var old = notification("me", null, NotificationType.SYSTEM, 1);
        notification("me", null, NotificationType.SYSTEM, 2);
        notifications.readAll("me", old.getId(), Instant.now());
        assertEquals(1, notifications.unreadCount("me"));
    }
    @Test void tokenRotationRemovesOnlyThisUsersPreviousInstallationToken() {
        for (String id : new String[]{"old","new","other"}) {
            var d=new PushDevice(); d.setId(id); d.setUserId(id.equals("other")?"someone-else":"me");
            d.setToken("token-"+id); d.setSessionVersion(0); d.setInstallationId("phone"); d.setAuthSessionId("session"); devices.saveAndFlush(d);
        }
        devices.deleteByUserIdAndInstallationIdAndIdNot("me","phone","new"); devices.flush();
        assertFalse(devices.existsById("old")); assertTrue(devices.existsById("new")); assertTrue(devices.existsById("other"));
    }
    @Test void deliveryReceiptCleanupPreservesCurrentDeduplicationRecords() {
        var old=new PushReceipt(); old.setId("1:old"); old.setCreatedAt(Instant.now().minusSeconds(200000)); receipts.saveAndFlush(old);
        var recent=new PushReceipt(); recent.setId("2:recent"); receipts.saveAndFlush(recent);
        receipts.deleteOlderThan(Instant.now().minusSeconds(172800));
        assertFalse(receipts.existsById("1:old")); assertTrue(receipts.existsById("2:recent"));
    }
    @Test void aSupportDeepLinkCannotReadAnotherAccountsTicket() {
        var ticket=new com.linkup.user.settings.SupportTicket(); ticket.owner="me"; ticket.category="GENERAL";
        ticket.subject="Question"; ticket.message="A private support request"; tickets.saveAndFlush(ticket);
        assertTrue(tickets.findByIdAndOwner(ticket.id,"me").isPresent());
        assertTrue(tickets.findByIdAndOwner(ticket.id,"someone-else").isEmpty());
    }
}
