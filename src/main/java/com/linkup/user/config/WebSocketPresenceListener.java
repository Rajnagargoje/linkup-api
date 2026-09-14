package com.linkup.user.config;

import com.linkup.user.entity.User;
import com.linkup.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WebSocketPresenceListener {

    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handleConnect(SessionConnectedEvent event) {
        Principal principal = event.getUser();
        if (principal == null) return;
        setPresence(principal.getName(), true);
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        Principal principal = event.getUser();
        if (principal == null) return;
        setPresence(principal.getName(), false);
    }

    private void setPresence(String username, boolean online) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setOnline(online);
            user.setLastSeenAt(LocalDateTime.now());
            userRepository.save(user);

            Map<String, Object> payload = new HashMap<>();
            payload.put("username", username);
            payload.put("status", online ? "ONLINE" : "OFFLINE");
            messagingTemplate.convertAndSend("/topic/presence", payload);
        });
    }
}
