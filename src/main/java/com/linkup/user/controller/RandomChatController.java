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
    public record MessageRequest(String matchId, String content) {}
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
        service.message(principal, sessionId, request.matchId(), request.content());
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
}
