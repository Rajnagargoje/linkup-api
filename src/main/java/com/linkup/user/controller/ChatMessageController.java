package com.linkup.user.controller;


import com.linkup.user.dto.chat.ChatMessageResponse;
import com.linkup.user.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    @PostMapping("/{conversationId}/messages")
    public ChatMessageResponse send(Principal principal, @PathVariable Long conversationId,
            @RequestBody com.linkup.user.dto.chat.SendMessageRequest request) {
        return chatMessageService.sendMessage(principal.getName(),
                new com.linkup.user.dto.chat.SendMessageRequest(conversationId, request.content(), request.replyToMessageId()));
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getMessages(
            Principal principal,
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {

        return ResponseEntity.ok(
                chatMessageService.getMessages(
                        principal.getName(),
                        conversationId,
                        page,
                        size
                )
        );
    }

    @PatchMapping("/messages/{messageId}")
    public ResponseEntity<ChatMessageResponse> editMessage(
            Principal principal,
            @PathVariable Long messageId,
            @RequestParam String content
    ) {

        return ResponseEntity.ok(
                chatMessageService.editMessage(
                        principal.getName(),
                        messageId,
                        content
                )
        );
    }

    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            Principal principal,
            @PathVariable Long messageId
    ) {

        chatMessageService.deleteForEveryone(
                principal.getName(),
                messageId
        );

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/messages/{messageId}/delivered")
    public ResponseEntity<Void> delivered(
            Principal principal,
            @PathVariable Long messageId
    ) {

        chatMessageService.markDelivered(
                principal.getName(),
                messageId
        );

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/messages/{messageId}/read")
    public ResponseEntity<Void> read(
            Principal principal,
            @PathVariable Long messageId
    ) {

        chatMessageService.markRead(
                principal.getName(),
                messageId
        );

        return ResponseEntity.noContent().build();
    }
}
