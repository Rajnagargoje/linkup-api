package com.linkup.user.controller;

import com.linkup.user.service.GuestSessionService;
import com.linkup.user.service.RandomChatService;
import com.linkup.user.utils.SimpleRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.Map;

@RestController @RequestMapping("/api/random") @RequiredArgsConstructor
public class GuestSessionController {
    public record StartGuest(String displayName) {}
    public record ClaimGuest(String guestToken) {}
    private final GuestSessionService guests;
    private final RandomChatService randomChat;
    private final SimpleRateLimiter limiter;

    @PostMapping("/guest")
    public ResponseEntity<?> create(@RequestBody StartGuest request, HttpServletRequest http) {
        // Do not trust a client-supplied forwarding header as a rate-limit identity.
        if (!limiter.tryConsume("guest-create:" + http.getRemoteAddr(), 20, 60_000))
            return ResponseEntity.status(429).body(Map.of("message", "Too many attempts. Please try again in a minute."));
        return ResponseEntity.ok(guests.create(request.displayName()));
    }
    @PostMapping("/claim")
    public Map<String, String> claim(@RequestBody ClaimGuest request, Principal principal) {
        randomChat.claimGuest(request.guestToken(), principal.getName());
        return Map.of("message", "Account linked. Your conversation is still open.");
    }
}
