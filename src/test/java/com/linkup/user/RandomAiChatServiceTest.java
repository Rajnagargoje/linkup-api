package com.linkup.user;

import com.linkup.user.randomai.*;
import com.linkup.user.service.*;
import com.linkup.user.utils.SimpleRateLimiter;
import org.junit.jupiter.api.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RandomAiChatServiceTest {
    RandomChatService service;
    SimpMessagingTemplate messaging;
    GuestSessionService identities;
    ChatRelationshipPolicy policy;
    ConnectionService connections;
    AiChatClient client;
    AiCompanionStore store;
    List<Map<String, Object>> events;
    Map<String, CompletableFuture<String>> replies;
    Map<String, Runnable> cancels;
    final RandomChatService.Preferences prefs = new RandomChatService.Preferences("Hinglish", List.of("music"), true);
    @BeforeEach void setUp() {
        events = new ArrayList<>(); replies = new HashMap<>(); cancels = new HashMap<>();
        messaging = mock(SimpMessagingTemplate.class);
        doAnswer(call -> { Map<String,Object> event = new LinkedHashMap<>(call.<Map<String,Object>>getArgument(2)); event.put("session", call.getArgument(0)); events.add(event); return null; })
            .when(messaging).convertAndSendToUser(anyString(), eq("/queue/random"), any(), anyMap());
        identities = mock(GuestSessionService.class);
        when(identities.resolve(any())).thenAnswer(call -> { Principal p = call.getArgument(0); return new GuestSessionService.Identity("u:" + p.getName(), p.getName(), p.getName(), p.getName()); });
        policy = mock(ChatRelationshipPolicy.class); when(policy.mayMatch(anyString(), anyString())).thenAnswer(call -> !call.getArgument(0).equals(call.getArgument(1)));
        connections = mock(ConnectionService.class); client = mock(AiChatClient.class); store = mock(AiCompanionStore.class);
        when(client.available()).thenReturn(true); when(client.fallbackSeconds()).thenReturn(15);
        when(client.reply(anyString(), any(), anyString(), anyList(), anyList())).thenAnswer(call -> {
            String owner = call.getArgument(0); CompletableFuture<String> result = new CompletableFuture<>(); Runnable cancel = mock(Runnable.class);
            replies.put(owner, result); cancels.put(owner, cancel); return new AiChatClient.Pending(result, cancel);
        });
        service = new RandomChatService(messaging, identities, policy, connections, new SimpleRateLimiter(),
            mock(com.linkup.user.repository.ChatReportRepository.class), mock(com.linkup.user.notification.NotificationService.class), client, store);
    }
    @AfterEach void stop() { service.shutdown(); }
    Map<String,Object> last(String session, String type) { return events.stream().filter(e -> session.equals(e.get("session")) && type.equals(e.get("type"))).reduce((a,b) -> b).orElseThrow(); }
    long count(String session, String type) { return events.stream().filter(e -> session.equals(e.get("session")) && type.equals(e.get("type"))).count(); }
    String ai(String name, String session) {
        service.join(() -> name, session, prefs); assertEquals(true, last(session, "WAITING").get("aiAvailable"));
        ReflectionTestUtils.invokeMethod(service, "fallback", session);
        assertEquals("AI", last(session,"MATCHED").get("partnerKind")); return (String) last(session,"MATCHED").get("matchId");
    }
    @Test void timerFallbackIsDisclosedAndNeverCreatesAFriend() {
        ai("alice","a"); assertEquals("ai:aanya", last("a","MATCHED").get("partnerId"));
        assertEquals(true, last("a","MATCHED").get("searching")); verifyNoInteractions(connections);
        assertEquals(List.of(), last("a", "MATCHED").get("messages"));
        assertEquals("f · 24 · Pune", last("a", "MATCHED").get("personaProfile"));
        assertEquals(0, count("a", "MESSAGE"));
        verify(client, never()).reply(anyString(), any(), anyString(), anyList(), anyList());
    }
    @Test void oldClientsAndPeopleOnlyPreferencesNeverStartAi() {
        service.join("alice","a"); assertEquals(false,last("a","WAITING").get("aiAvailable"));
        // A timer cannot exist for this preference; invoke defensively too.
        ReflectionTestUtils.invokeMethod(service,"fallback","a");
        assertEquals(0,count("a","MATCHED"));
    }
    @Test void humanMatchWinsWhenSomeoneArrivesBeforeTheTimer() {
        service.join(() -> "alice","a",prefs); service.join(() -> "bob","b",prefs);
        ReflectionTestUtils.invokeMethod(service,"fallback","a");
        assertEquals("HUMAN",last("a","MATCHED").get("partnerKind")); assertEquals(1,count("a","MATCHED"));
    }
    @Test void cancellingSearchPreventsLateFallback() {
        service.join(() -> "alice","a",prefs); service.leave("a"); ReflectionTestUtils.invokeMethod(service,"fallback","a");
        assertEquals(0,count("a","MATCHED"));
    }
    @Test void differentUsersHaveIsolatedContextAndReplies() {
        when(policy.mayMatch(anyString(),anyString())).thenReturn(false);
        String a=ai("alice","a"),b=ai("bob","b");
        service.message(() -> "alice","a",a,"My favourite food is dosa","a1");
        service.message(() -> "bob","b",b,"I like cricket","b1");
        replies.get("u:alice").complete("Dosa is a fun topic!");
        assertEquals("Dosa is a fun topic!",last("a","MESSAGE").get("content"));
        assertEquals("I like cricket",last("b","MESSAGE").get("content"));
        verify(client).reply(eq("u:bob"),any(),eq("hinglish"),anyList(),argThat(history -> history.stream().noneMatch(t -> t.content().contains("dosa"))));
    }
    @Test void handoffRequiresConsentAndCancelsOldAiReplies() {
        String a=ai("alice","a"); service.message(() -> "alice","a",a,"hello","a1");
        service.join("bob","b"); String offer=(String)last("a","HUMAN_OFFER").get("offerId");
        assertEquals(0,count("b","MATCHED")); assertEquals(0,count("b","MESSAGE"));
        service.respondOffer(() -> "alice","a",offer,true);
        assertEquals("HUMAN",last("a","MATCHED").get("partnerKind"));
        assertNotEquals(a,last("a","MATCHED").get("matchId")); verify(cancels.get("u:alice")).run();
        long before=count("a","MESSAGE"); replies.get("u:alice").complete("This is too late"); assertEquals(before,count("a","MESSAGE"));
        assertEquals(0,count("b","MESSAGE"));
    }
    @Test void declineReturnsOtherPersonToQueueAndKeepsCompanion() {
        String a=ai("alice","a"); service.join("bob","b");
        service.respondOffer(() -> "alice","a",(String)last("a","HUMAN_OFFER").get("offerId"),false);
        assertEquals(false,last("a","OFFER_ENDED").get("searching")); assertEquals(true,last("b","WAITING").containsKey("aiAvailable"));
        service.message(() -> "alice","a",a,"Still here","a1"); assertEquals("Still here",last("a","MESSAGE").get("content"));
    }
    @Test void twoAiChatsRequireBothPeopleToAcceptBeforeTheirHumanThreadStarts() {
        when(policy.mayMatch(anyString(),anyString())).thenReturn(false);
        String a = ai("alice", "a"); ai("bob", "b");
        when(policy.mayMatch(anyString(),anyString())).thenReturn(true);
        service.searchPeople(() -> "alice", "a", a, true);
        String offer = (String)last("a", "HUMAN_OFFER").get("offerId");
        assertEquals(offer, last("b", "HUMAN_OFFER").get("offerId"));
        service.respondOffer(() -> "alice", "a", offer, true);
        assertEquals("AI", last("a", "MATCHED").get("partnerKind"));
        assertEquals("AI", last("b", "MATCHED").get("partnerKind"));
        service.respondOffer(() -> "bob", "b", offer, true);
        assertEquals("HUMAN", last("a", "MATCHED").get("partnerKind"));
        assertEquals(last("a", "MATCHED").get("matchId"), last("b", "MATCHED").get("matchId"));
        assertEquals(0, count("a", "MESSAGE")); assertEquals(0, count("b", "MESSAGE"));
    }
    @Test void duplicateMessageRetriesDoNotDuplicateAiCalls() {
        String a=ai("alice","a"); service.message(() -> "alice","a",a,"hello","same"); service.message(() -> "alice","a",a,"hello","same");
        verify(client,times(1)).reply(anyString(),any(),anyString(),anyList(),anyList());
        assertEquals(last("a","MESSAGE").get("id"),events.stream().filter(e -> "MESSAGE".equals(e.get("type"))).findFirst().orElseThrow().get("id"));
    }
    @Test void failedReplyCanRetryWithoutDuplicatingUserText() {
        String a=ai("alice","a"); service.message(() -> "alice","a",a,"hello","a1");
        replies.get("u:alice").completeExceptionally(new RuntimeException("provider detail must not reach user"));
        assertFalse(last("a","AI_ERROR").get("message").toString().contains("provider detail"));
        service.retryAi(() -> "alice","a",a); replies.get("u:alice").complete("Hello again");
        assertEquals(2,count("a","MESSAGE")); verify(client,times(2)).reply(anyString(),any(),anyString(),anyList(),anyList());
    }
    @Test void aiKeepInTouchNeverCreatesOrAcceptsHumanConnections() {
        String a=ai("alice","a"); service.connect(() -> "alice","a",a); service.acceptConnection(() -> "alice","a",a);
        verifyNoInteractions(connections); service.saveCompanion(() -> "alice","a",a);
        verify(store).save(eq("alice"),eq("aanya"),anyList(),eq(true)); assertEquals(true,last("a","COMPANION_SAVED").get("saved"));
    }
    @Test void savedCompanionLoadsOnlyItsOwnersHistory() {
        when(store.load("alice","aanya")).thenReturn(List.of(AiTurn.create("ai:aanya","Aanya","Welcome back",null)));
        service.resumeCompanion(() -> "alice","a","aanya",prefs);
        verify(store).load("alice","aanya"); assertEquals(false,last("a","MATCHED").get("searching"));
        assertEquals(true,last("a","MATCHED").get("saved"));
        assertEquals(0, count("a", "MESSAGE"));
        verify(client, never()).reply(anyString(), any(), anyString(), anyList(), anyList());
    }
    @Test void forgedPrincipalOrStaleMatchCannotSendSaveOrHandoff() {
        String a=ai("alice","a"); service.message(() -> "mallory","a",a,"wrong user","a1");
        service.saveCompanion(() -> "mallory","a",a); service.message(() -> "alice","a","old-match","old","a2");
        verify(client,never()).reply(anyString(),any(),anyString(),anyList(),anyList()); verifyNoInteractions(store);
    }
    @Test void requestSentReceivedAndAcceptedStatesUseRealConnections() {
        service.join("alice","a"); service.join("bob","b"); String match=(String)last("a","MATCHED").get("matchId");
        when(connections.getConnectionStatus("alice","bob")).thenReturn("NONE","REQUEST_SENT");
        when(connections.getConnectionStatus("bob","alice")).thenReturn("REQUEST_RECEIVED");
        service.connect(() -> "alice","a",match); verify(connections).sendRequest("alice","bob");
        assertEquals("REQUEST_SENT",last("a","CONNECTION").get("connectionStatus")); assertEquals("REQUEST_RECEIVED",last("b","CONNECTION").get("connectionStatus"));
        var request=new com.linkup.user.dto.response.ConnectionResponseDTO(); request.setConnectionId(123L);request.setUserId("alice");
        when(connections.getReceivedRequests("bob")).thenReturn(List.of(request));
        when(connections.getConnectionStatus(anyString(),anyString())).thenReturn("CONNECTED");
        service.acceptConnection(() -> "bob","b",match);verify(connections).acceptRequest("bob",123L);
        assertEquals("CONNECTED",last("a","CONNECTION").get("connectionStatus"));assertEquals("CONNECTED",last("b","CONNECTION").get("connectionStatus"));
    }
    @Test void disconnectDuringAiReplyDoesNotDeliverAfterLeave() {
        String a=ai("alice","a");service.message(() -> "alice","a",a,"hello","a1");service.leave("a");
        verify(cancels.get("u:alice")).run(); long before=count("a","MESSAGE");replies.get("u:alice").complete("late");assertEquals(before,count("a","MESSAGE"));
    }
}
