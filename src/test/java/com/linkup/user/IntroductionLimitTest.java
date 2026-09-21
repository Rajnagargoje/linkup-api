package com.linkup.user;
import com.linkup.user.entity.*;
import com.linkup.user.entity.chat.*;
import com.linkup.user.repository.*;
import com.linkup.user.service.ChatRelationshipPolicy;
import com.linkup.user.service.impl.ChatMessageServiceImpl;
import com.linkup.user.dto.chat.SendMessageRequest;
import com.linkup.user.utils.ConversationType;
import org.junit.jupiter.api.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IntroductionLimitTest {
    ChatMessageRepository messages;
    ChatRelationshipPolicy policy;
    ChatMessageServiceImpl service;
    @BeforeEach void setup() {
        messages = mock(ChatMessageRepository.class);
        var conversations = mock(ConversationRepository.class);
        var participants = mock(ConversationParticipantRepository.class);
        var users = mock(UserRepository.class); policy = mock(ChatRelationshipPolicy.class);
        var sender = new User(); sender.setId(1L); sender.setPublicId("alice"); sender.setUsername("alice");
        var recipient = new User(); recipient.setId(2L); recipient.setPublicId("bob"); recipient.setUsername("bob");
        var conversation = new Conversation(); conversation.setId(1L); conversation.setType(ConversationType.DIRECT);
        var a = new ConversationParticipant(); a.setUser(sender); a.setConversation(conversation);
        var b = new ConversationParticipant(); b.setUser(recipient); b.setConversation(conversation);
        conversation.setParticipants(List.of(a,b));
        when(users.findByUsernameForUpdate("alice")).thenReturn(Optional.of(sender));
        when(conversations.findById(1L)).thenReturn(Optional.of(conversation));
        when(participants.findByConversationIdAndUserPublicId(1L,"alice")).thenReturn(Optional.of(a));
        when(messages.save(any())).thenAnswer(call -> { ChatMessage message = call.getArgument(0); message.setId(1L); return message; });
        service = new ChatMessageServiceImpl(messages, conversations, participants, users, mock(SimpMessagingTemplate.class), policy, mock(com.linkup.user.notification.NotificationService.class));
    }
    @Test void rejectsFourthIntroductionBeforeSavingOrBroadcasting() {
        when(messages.countBetween("alice", "bob")).thenReturn(3L);
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage("alice", new SendMessageRequest(1L,"fourth",null)));
        verify(messages, never()).save(any());
    }
    @Test void permitsThirdIntroduction() {
        when(messages.countBetween("alice", "bob")).thenReturn(2L);
        assertEquals("third", service.sendMessage("alice", new SendMessageRequest(1L,"third",null)).content());
    }
    @Test void friendshipRemovesIntroductionLimit() {
        when(policy.friends("u:alice", "u:bob")).thenReturn(true);
        assertEquals("hello", service.sendMessage("alice", new SendMessageRequest(1L,"hello",null)).content());
        verify(messages, never()).countBetween(anyString(), anyString());
    }
    @Test void blockStopsEvenAnExistingConversation() {
        doThrow(new IllegalArgumentException("Unavailable")).when(policy).ensureContact(any(), any());
        assertThrows(IllegalArgumentException.class, () -> service.sendMessage("alice", new SendMessageRequest(1L,"hello",null)));
        verify(messages, never()).save(any());
    }
    @Test void blockAlsoStopsEditingAnOldMessageToSendNewContent() {
        service.sendMessage("alice", new SendMessageRequest(1L,"hello",null));
        var captor = org.mockito.ArgumentCaptor.forClass(ChatMessage.class);
        verify(messages).save(captor.capture());
        when(messages.findById(1L)).thenReturn(Optional.of(captor.getValue()));
        doThrow(new IllegalArgumentException("Unavailable")).when(policy).ensureContact(any(), any());
        assertThrows(IllegalArgumentException.class, () -> service.editMessage("alice", 1L, "new content"));
        verify(messages, times(1)).save(any());
    }
}
