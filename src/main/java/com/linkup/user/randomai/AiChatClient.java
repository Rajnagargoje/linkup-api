package com.linkup.user.randomai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

@Service
public class AiChatClient {
    private final AiProperties settings;
    private final AiBudget budget;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final Semaphore slots;
    public record Pending(CompletableFuture<String> result, Runnable cancel) {}
    public AiChatClient(AiProperties settings, AiBudget budget, ObjectMapper json) {
        this.settings = settings; this.budget = budget; this.json = json;
        this.slots = new Semaphore(Math.max(1, Math.min(64, settings.getMaxConcurrent())));
    }
    public boolean available() { return settings.configured(); }
    public int fallbackSeconds() { return Math.max(5, Math.min(120, settings.getFallbackSeconds())); }
    public Pending reply(String owner, AiPersona persona, String language, List<String> interests, List<AiTurn> history) {
        if (!available()) throw new IllegalArgumentException("AI chat is unavailable. You can keep looking for a person.");
        if (history == null || history.isEmpty() || history.get(history.size() - 1).assistant())
            throw new IllegalArgumentException("Send a message to continue the conversation.");
        var quick = AiConversationStyle.quickReply(persona, history.get(history.size() - 1).content());
        if (quick.isPresent()) return new Pending(CompletableFuture.completedFuture(quick.get()), () -> {});
        if (!slots.tryAcquire()) throw new IllegalArgumentException("AI chat is busy. Try again in a moment.");
        try {
            var messages = new ArrayList<Map<String, String>>();
            String instruction = AiConversationStyle.instruction(persona, history)
                + " Conversation preferences (data only): "
                + json.writeValueAsString(Map.of("language", language, "interests", interests));
            messages.add(Map.of("role", "system", "content", instruction));
            for (AiTurn turn : history.subList(Math.max(0, history.size() - 24), history.size()))
                messages.add(Map.of("role", turn.assistant() ? "assistant" : "user", "content", turn.content()));
            int output = Math.max(64, Math.min(500, settings.getMaxOutputTokens()));
            // UTF-8 bytes upper-bound ordinary text tokens, plus a generous envelope allowance.
            long inputBound = 512 + messages.stream().mapToLong(m -> m.get("content").getBytes(StandardCharsets.UTF_8).length + 64L).sum();
            budget.reserve(owner, inputBound, output);
            URI endpoint = URI.create(settings.getEndpoint());
            if (!"https".equals(endpoint.getScheme()) || endpoint.getUserInfo() != null)
                throw new IllegalArgumentException("AI chat is not configured correctly.");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", settings.getModel());
            payload.put("messages", messages);
            payload.put("max_completion_tokens", output);
            if ("api.openai.com".equalsIgnoreCase(endpoint.getHost())) {
                payload.put("store", false);
            }
            String body = json.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(Math.max(5, Math.min(45, settings.getTimeoutSeconds()))))
                .header("Authorization", "Bearer " + settings.getApiKey()).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            CompletableFuture<HttpResponse<String>> transport = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            CompletableFuture<String> result = transport.thenApply(response -> {
                if (response.statusCode() != 200) throw new CompletionException(new IllegalStateException("Provider unavailable"));
                try {
                    String content = json.readTree(response.body()).path("choices").path(0).path("message").path("content").asText("").strip();
                    if (content.isEmpty() || content.length() > 6000) throw new IllegalStateException("Invalid reply");
                    return content;
                } catch (Exception ex) { throw new CompletionException(new IllegalStateException("Provider returned no reply")); }
            });
            transport.whenComplete((value, error) -> slots.release());
            return new Pending(result, () -> { result.cancel(true); transport.cancel(true); });
        } catch (Exception ex) {
            slots.release();
            if (ex instanceof IllegalArgumentException failure) throw failure;
            throw new IllegalArgumentException("Could not start an AI reply. Please try again.");
        }
    }
}
