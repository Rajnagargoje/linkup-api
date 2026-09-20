package com.linkup.user;

import com.linkup.user.service.RandomChatService;
import com.linkup.user.service.GuestSessionService;
import com.linkup.user.service.ChatRelationshipPolicy;
import com.linkup.user.service.ConnectionService;
import com.linkup.user.utils.SimpleRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RandomChatServiceTest {
    private SimpMessagingTemplate messaging;
    private RandomChatService service;
    private ChatRelationshipPolicy policy;
    private GuestSessionService identities;
    private ConnectionService connections;

    @BeforeEach
    void setup() {
        messaging = mock(SimpMessagingTemplate.class);
        identities = mock(GuestSessionService.class);
        when(identities.resolve(any())).thenAnswer(call -> {
            java.security.Principal p = call.getArgument(0);
            return new GuestSessionService.Identity("u:" + p.getName(), p.getName(), p.getName(), p.getName());
        });
        policy = mock(ChatRelationshipPolicy.class);
        when(policy.mayMatch(anyString(), anyString())).thenReturn(true);
        connections = mock(ConnectionService.class);
        service = new RandomChatService(messaging, identities, policy, connections, new SimpleRateLimiter(), mock(com.linkup.user.repository.ChatReportRepository.class));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> latest(String session) {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messaging, atLeastOnce()).convertAndSendToUser(eq(session), eq("/queue/random"), payload.capture(), anyMap());
        return (Map<String, Object>) payload.getValue();
    }

    private String pair() {
        service.join("alice", "a");
        assertEquals("WAITING", latest("a").get("type"));
        service.join("bob", "b");
        assertEquals("MATCHED", latest("a").get("type"));
        assertEquals("bob", latest("a").get("partner"));
        assertEquals(latest("a").get("matchId"), latest("b").get("matchId"));
        return (String) latest("a").get("matchId");
    }

    @Test
    void deliversOnlyToMatchedSessionsWithServerIdentity() {
        String match = pair();
        clearInvocations(messaging);
        service.message("alice", "a", match, "Hello");
        assertEquals("alice", latest("b").get("sender"));
        assertEquals(latest("a"), latest("b"));
        verify(messaging, times(2)).convertAndSendToUser(anyString(), eq("/queue/random"), any(), anyMap());
    }

    @Test
    void rejectsStaleMatchesAndForgedSessionIdentity() {
        String match = pair();
        clearInvocations(messaging);
        service.message("alice", "a", "old-match", "Hello");
        service.message("mallory", "a", match, "Hello");
        assertEquals("ERROR", latest("a").get("type"));
        verify(messaging, never()).convertAndSendToUser(eq("b"), anyString(), any(), anyMap());
    }

    @Test
    void leavingEndsBothSidesAndAllowsNewMatches() {
        String oldMatch = pair();
        service.leave("a");
        assertEquals("ENDED", latest("b").get("type"));
        service.join("alice", "a");
        assertEquals("WAITING", latest("a").get("type"));
        service.join("carol", "c");
        assertNotEquals(oldMatch, latest("a").get("matchId"));
        assertEquals("carol", latest("a").get("partner"));
    }

    @Test
    void cancellationRemovesUserFromQueueAndIsIdempotent() {
        service.join("alice", "a");
        service.leave("a");
        service.leave("a");
        service.join("bob", "b");
        assertEquals("WAITING", latest("b").get("type"));
        assertEquals("ENDED", latest("a").get("type"));
    }

    @Test
    void duplicateJoinsAndSecondTabsCannotSelfMatchOrBreakPair() {
        service.join("alice", "a");
        service.join("alice", "a");
        service.join("alice", "a2");
        assertEquals("ERROR", latest("a2").get("type"));
        service.join("bob", "b");
        String match = (String) latest("a").get("matchId");
        service.join("alice", "a");
        service.message("alice", "a", match, "Still here");
        assertEquals("MESSAGE", latest("b").get("type"));
    }

    @Test
    void rejectsEmptyAndOversizedMessages() {
        String match = pair();
        clearInvocations(messaging);
        service.message("alice", "a", match, " ");
        service.message("alice", "a", match, "x".repeat(2001));
        assertEquals("ERROR", latest("a").get("type"));
        verify(messaging, never()).convertAndSendToUser(eq("b"), anyString(), any(), anyMap());
    }

    @Test void excludesFriendsAndBlockedPeopleFromQueue() {
        when(policy.mayMatch("u:bob", "u:alice")).thenReturn(false);
        service.join("alice", "a"); service.join("bob", "b");
        assertEquals("WAITING", latest("b").get("type"));
        service.join("carol", "c");
        assertEquals("carol", latest("a").get("partner"));
        assertEquals("WAITING", latest("b").get("type"));
    }

    @Test void guestAndAccountSharePoolButDisplayNamesAreNotIdentities() {
        var guest = new GuestSessionService.GuestPrincipal("guest-id");
        when(identities.resolve(guest)).thenReturn(new GuestSessionService.Identity("g:guest-id", "alice", null, null));
        service.join("alice", "a");
        service.join(guest, "g", null);
        assertEquals("MATCHED", latest("a").get("type"));
        assertEquals(true, latest("a").get("partnerGuest"));
        service.connect(guest, "g", (String) latest("g").get("matchId"));
        assertEquals("REGISTER_REQUIRED", latest("g").get("type"));
        verifyNoInteractions(connections);
    }

    @Test void prefersSharedLanguageAmongAvailableCandidates() {
        when(policy.mayMatch("u:bob", "u:alice")).thenReturn(false);
        service.join(() -> "alice", "a", new RandomChatService.Preferences("English", List.of("books")));
        service.join(() -> "bob", "b", new RandomChatService.Preferences("Hindi", List.of("games")));
        service.join(() -> "carol", "c", new RandomChatService.Preferences("Hindi", List.of()));
        assertEquals("bob", latest("c").get("partner"));
    }

    @Test void linkingGuestUpdatesMatchWithoutLosingConversation() {
        var guest = new GuestSessionService.GuestPrincipal("guest-id");
        var guestIdentity = new GuestSessionService.Identity("g:guest-id", "Visitor", null, null);
        when(identities.resolve(guest)).thenReturn(guestIdentity);
        when(identities.authenticate("token")).thenReturn(guest);
        service.join(guest, "g", null); service.join("bob", "b");
        String match = (String) latest("g").get("matchId");
        when(identities.claim("token", "alice")).thenAnswer(call -> {
            when(identities.resolve(guest)).thenReturn(new GuestSessionService.Identity("u:alice", "alice", "alice", "alice"));
            return guest;
        });
        service.claimGuest("token", "alice");
        assertEquals("MATCH_UPDATED", latest("b").get("type"));
        assertEquals(match, latest("g").get("matchId"));
        service.message(guest, "g", match, "Still chatting");
        assertEquals("Still chatting", latest("b").get("content"));
    }
}

