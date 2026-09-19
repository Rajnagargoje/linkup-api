package com.linkup.user.controller;


import com.linkup.user.dto.chat.ChatMessageResponse;
import com.linkup.user.dto.chat.SendMessageRequest;
import com.linkup.user.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class FriendChatWebSocketController {

    private final ChatMessageService chatMessageService;

    @MessageMapping("/chat.send")
    public ChatMessageResponse sendMessage(
            SendMessageRequest request,
            Principal principal
    ) {

        if (principal == null) {
            throw new IllegalStateException(
                    "Unauthenticated WebSocket session"
            );
        }

        return chatMessageService.sendMessage(
                principal.getName(),
                request
        );
    }
}