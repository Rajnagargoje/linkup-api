package com.linkup.user;

import com.linkup.user.entity.User;
import com.linkup.user.notification.*;
import com.linkup.user.repository.UserRepository;
import com.linkup.user.settings.*;
import org.junit.jupiter.api.*;
import java.util.Optional;
import static org.mockito.Mockito.*;

class SupportNotificationTest {
    final SupportTicketRepository tickets=mock(SupportTicketRepository.class);
    final NotificationService notifications=mock(NotificationService.class);
    final UserRepository users=mock(UserRepository.class);
    final SupportAdminController controller=new SupportAdminController(tickets,notifications,users);
    SupportTicket ticket; User owner;
    @BeforeEach void setup() {
        ticket=new SupportTicket(); ticket.id=7L; ticket.owner="owner"; ticket.status="OPEN";
        owner=new User(); owner.setPublicId("owner");
        when(tickets.findById(7L)).thenReturn(Optional.of(ticket)); when(tickets.save(ticket)).thenReturn(ticket);
        when(users.findByPublicId("owner")).thenReturn(Optional.of(owner));
    }
    @Test void replyCreatesAnOwnerOnlyNotificationWithoutExposingThePrivateReply() {
        controller.update(7L,new SupportAdminController.Update("IN_PROGRESS","Private support reply"));
        verify(notifications).create(eq(owner),isNull(),eq(NotificationType.SUPPORT_REPLY),eq(7L),isNull(),
            eq("Support replied to your request"),eq("Open request #7 to see the latest update."),eq(false));
    }
    @Test void repeatedIdenticalAdminSaveDoesNotSendDuplicateAlerts() {
        controller.update(7L,new SupportAdminController.Update("RESOLVED","Done"));
        controller.update(7L,new SupportAdminController.Update("RESOLVED","Done"));
        verify(notifications,times(1)).create(any(),isNull(),eq(NotificationType.SUPPORT_REPLY),eq(7L),isNull(),anyString(),anyString(),eq(false));
    }
    @Test void statusOnlyUpdatesAlsoNotifyTheRequester() {
        controller.update(7L,new SupportAdminController.Update("IN_PROGRESS",null));
        verify(notifications).create(eq(owner),isNull(),eq(NotificationType.SUPPORT_REPLY),eq(7L),isNull(),
            eq("Your support request was updated"),anyString(),eq(false));
    }
}
