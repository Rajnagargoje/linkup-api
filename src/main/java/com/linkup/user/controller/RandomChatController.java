package com.linkup.user.controller;

import com.linkup.user.service.RandomChatService;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class RandomChatController {
    public record MessageRequest(String matchId, String content, String clientId) {}
    public record ActionRequest(String matchId, String personaId, String offerId, Boolean accept, Boolean searching, RandomChatService.Preferences preferences, Boolean typing) {}
    private final RandomChatService service;

    public RandomChatController(RandomChatService service) { this.service = service; }

    @MessageMapping("/random/join")
    public void join(RandomChatService.Preferences preferences, Principal principal, @Header("simpSessionId") String sessionId) {
        service.join(principal, sessionId, preferences);
    }

    @MessageMapping("/random/leave")
    public void leave(Principal principal, @Header("simpSessionId") String sessionId) {
        if (principal != null) service.leave(sessionId);
    }

    @MessageMapping("/random/message")
    public void message(MessageRequest request, Principal principal, @Header("simpSessionId") String sessionId) {
        service.message(principal, sessionId, request.matchId(), request.content(), request.clientId());
    }

    @MessageMapping("/random/connect")
    public void connect(MessageRequest request, Principal principal, @Header("simpSessionId") String sessionId) {
        service.connect(principal, sessionId, request.matchId());
    }
    @MessageMapping("/random/block")
    public void block(MessageRequest request, Principal principal, @Header("simpSessionId") String sessionId) {
        service.block(principal, sessionId, request.matchId());
    }
    @MessageExceptionHandler({IllegalArgumentException.class, org.springframework.security.authentication.BadCredentialsException.class})
    @SendToUser(value = "/queue/random", broadcast = false)
    public java.util.Map<String, String> error(Exception ex) {
        return java.util.Map.of("type", "ERROR", "message", ex.getMessage());
    }
    @MessageMapping("/random/report")
    public void report(MessageRequest request, Principal principal, @Header("simpSessionId") String sessionId) {
        service.report(principal, sessionId, request.matchId(), request.content());
    }

    @MessageMapping("/random/offer")
    public void offer(ActionRequest request, Principal principal, @Header("simpSessionId") String sessionId) {
        service.respondOffer(principal, sessionId, request.offerId(), Boolean.TRUE.equals(request.accept()));
    }
    @MessageMapping("/random/search-people")
    public void searchPeople(ActionRequest request, Principal principal, @Header("simpSessionId") String sessionId) {
        service.searchPeople(principal, sessionId, request.matchId(), Boolean.TRUE.equals(request.searching()));
    }
    @MessageMapping("/random/typing")
    public void typing(ActionRequest request, Principal principal, @Header("simpSessionId") String sessionId) {
        service.typing(principal, sessionId, request.matchId(), Boolean.TRUE.equals(request.typing()));
    }
    @MessageMapping("/random/ai/retry")
    public void retry(ActionRequest request, Principal principal, @Header("simpSessionId") String sessionId) {
        service.retryAi(principal, sessionId, request.matchId());
    }
    @MessageMapping("/random/companions/list")
    public void list(Principal principal, @Header("simpSessionId") String sessionId) { service.companions(principal, sessionId); }
    @MessageMapping("/random/companions/save")
    public void save(ActionRequest request, Principal principal, @Header("simpSessionId") String sessionId) { service.saveCompanion(principal, sessionId, request.matchId()); }
    @MessageMapping("/random/companions/remove")
    public void remove(ActionRequest request, Principal principal, @Header("simpSessionId") String sessionId) { service.removeCompanion(principal, sessionId, request.personaId()); }
    @MessageMapping("/random/companions/resume")
    public void resume(ActionRequest request, Principal principal, @Header("simpSessionId") String sessionId) { service.resumeCompanion(principal, sessionId, request.personaId(), request.preferences()); }
    @MessageMapping("/random/connection/accept")
    public void accept(ActionRequest request, Principal principal, @Header("simpSessionId") String sessionId) { service.acceptConnection(principal, sessionId, request.matchId()); }
    @MessageMapping("/random/connection/status")
    public void status(ActionRequest request, Principal principal, @Header("simpSessionId") String sessionId) { service.relationship(principal, sessionId, request.matchId()); }
    @MessageExceptionHandler(org.springframework.dao.DataAccessException.class)
    @SendToUser(value = "/queue/random", broadcast = false)
    public java.util.Map<String, String> dataError(Exception ignored) {
        return java.util.Map.of("type", "ERROR", "message", "Could not complete that action. Please try again.");
    }
}
