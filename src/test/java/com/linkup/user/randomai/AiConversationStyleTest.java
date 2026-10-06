package com.linkup.user.randomai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiConversationStyleTest {
    final AiPersona aanya = AiPersona.find("aanya");
    AiProperties settings;
    AiBudget budget;
    AiChatClient client;
    HttpClient http;
    ObjectMapper json = new ObjectMapper();

    @BeforeEach void setUp() {
        settings = new AiProperties(); settings.setEnabled(true); settings.setApiKey("test-only");
        budget = mock(AiBudget.class); http = mock(HttpClient.class);
        client = new AiChatClient(settings, budget, json);
        ReflectionTestUtils.setField(client, "http", http);
    }
    AiTurn user(String text) { return AiTurn.create("u:alice", "alice", text, UUID.randomUUID().toString()); }
    AiTurn ai(String text) { return AiTurn.create("ai:aanya", "Aanya", text, null); }
    String reply(AiPersona persona, String text) {
        return client.reply("u:alice", persona, "hinglish", List.of(), List.of(user(text))).result().join();
    }
    @Test void greetingRepliesOnlyToTheGreetingWithoutAProviderCall() {
        assertEquals("hi", reply(aanya, "hi"));
        assertEquals("hi", reply(aanya, " Hiii!! "));
        assertEquals("hello", reply(aanya, "hello"));
        assertEquals("hey", reply(aanya, "heyy"));
        verifyNoInteractions(http, budget);
    }
    @Test void genderQuestionsHandleSpacingAndTheMatchedCharacter() {
        for (String text : List.of("m?", "m ?", "M??", "f?", "m/f", "gender?")) {
            assertEquals("f", reply(aanya, text));
            assertEquals("m", reply(AiPersona.find("kabir"), text));
        }
        verifyNoInteractions(http, budget);
    }
    @Test void characterDetailsStayConsistentWithoutExtraQuestions() {
        assertEquals("Pune", reply(aanya, "from?"));
        assertEquals("Hyderabad", reply(AiPersona.find("kabir"), "where r u from?"));
        assertEquals("Bengaluru", reply(AiPersona.find("tara"), "kahan se?"));
        assertEquals("B.Com", reply(aanya, "what are you studying?"));
        assertEquals("music, food, travel", reply(aanya, "hobbies?"));
    }
    @Test void ambiguousOrLongerMessagesUseTheConversationInsteadOfAKeywordReply() {
        for (String text : List.of("m", "f", "u?", "from Pune to Goa?", "hi, how was your day?", "my name is Rahul"))
            assertTrue(AiConversationStyle.quickReply(aanya, text).isEmpty(), text);
    }
    @Test void identityQuestionsRemainHonest() {
        assertEquals("I'm Aanya, an AI character.", reply(aanya, "are you real?"));
        assertEquals("I'm Kabir, an AI character.", reply(AiPersona.find("kabir"), "are you human?"));
    }
    @Test void cannotGenerateAnOpeningMessageOrReplyToItsOwnMessage() {
        assertThrows(IllegalArgumentException.class, () -> client.reply("u:alice", aanya, "", List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> client.reply("u:alice", aanya, "", List.of(), List.of(ai("hi"))));
        verifyNoInteractions(http, budget);
    }
    @Test void followUpQuestionsWaitForConversationAndAreNotConsecutive() {
        List<AiTurn> turns = new ArrayList<>(List.of(user("hi"), ai("hi"), user("m?"), ai("f"), user("from?")));
        assertFalse(AiConversationStyle.mayAskFollowUp(turns));
        turns.add(ai("Pune")); turns.add(user("I like travel"));
        assertTrue(AiConversationStyle.mayAskFollowUp(turns));
        turns.add(ai("nice, where would you like to go?")); turns.add(user("Goa"));
        assertFalse(AiConversationStyle.mayAskFollowUp(turns));
        turns.add(ai("sounds good")); turns.add(user("with friends"));
        assertFalse(AiConversationStyle.mayAskFollowUp(turns));
        turns.add(ai("that sounds fun")); turns.add(user("next month"));
        assertTrue(AiConversationStyle.mayAskFollowUp(turns));
    }
    @SuppressWarnings("unchecked")
    String captureProviderBody() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"choices\":[{\"message\":{\"content\":\"music and travel\"}}]}");
        when(http.sendAsync(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
            .thenReturn(CompletableFuture.completedFuture(response));
        var history = List.of(user("what are your hobbies?"), ai("music and travel"), user("what kind of music do you like?"));
        assertEquals("music and travel", client.reply("u:alice", aanya, "hinglish", List.of("music"), history).result().join());
        var capture = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).sendAsync(capture.capture(), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        var body = new java.io.ByteArrayOutputStream();
        var done = new CompletableFuture<Void>();
        capture.getValue().bodyPublisher().orElseThrow().subscribe(new java.util.concurrent.Flow.Subscriber<>() {
            public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(java.nio.ByteBuffer buffer) { byte[] bytes = new byte[buffer.remaining()]; buffer.get(bytes); body.writeBytes(bytes); }
            public void onError(Throwable error) { done.completeExceptionally(error); }
            public void onComplete() { done.complete(null); }
        });
        done.join(); return body.toString(java.nio.charset.StandardCharsets.UTF_8);
    }
    @Test void providerGetsActualChatHistoryAndKeepsGroqCompatibility() throws Exception {
        settings.setEndpoint("https://api.groq.com/openai/v1/chat/completions");
        var body = json.readTree(captureProviderBody());
        assertFalse(body.has("store"));
        var messages = body.path("messages");
        assertEquals(4, messages.size());
        assertEquals("system", messages.get(0).path("role").asText());
        assertEquals("what are your hobbies?", messages.get(1).path("content").asText());
        assertEquals("music and travel", messages.get(2).path("content").asText());
        assertEquals("what kind of music do you like?", messages.get(3).path("content").asText());
        verify(budget).reserve(eq("u:alice"), anyLong(), eq(220));
    }
    @Test void openAiStillExplicitlyDisablesResponseStorage() throws Exception {
        assertFalse(json.readTree(captureProviderBody()).path("store").asBoolean(true));
    }
}
