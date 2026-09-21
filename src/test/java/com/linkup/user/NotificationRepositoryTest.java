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
}
