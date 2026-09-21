package com.linkup.user.controller;

import com.linkup.user.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping("/direct/{friendPublicId}")
    public ResponseEntity<?> createDirectConversation(
            Principal principal,
            @PathVariable String friendPublicId
    ) {

        return ResponseEntity.ok(
                conversationService
                        .getOrCreateDirectConversation(
                                principal.getName(),
                                friendPublicId
                        )
        );
    }

    @GetMapping
    public ResponseEntity<?> getConversations(
            Principal principal
    ) {

        return ResponseEntity.ok(
                conversationService.getMyConversations(
                        principal.getName()
                )
        );
    }

    @PatchMapping("/{conversationId}/read")
    public ResponseEntity<Void> markAsRead(
            Principal principal,
            @PathVariable Long conversationId,
            @RequestParam Long messageId
    ) {

        conversationService.markAsRead(
                conversationId,
                principal.getName(),
                messageId
        );

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{conversationId}/mute")
    public ResponseEntity<Void> mute(
            Principal principal,
            @PathVariable Long conversationId,
            @RequestParam boolean value
    ) {

        conversationService.setMuted(
                conversationId,
                principal.getName(),
                value
        );

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{conversationId}/archive")
    public ResponseEntity<Void> archive(
            Principal principal,
            @PathVariable Long conversationId,
            @RequestParam boolean value
    ) {

        conversationService.setArchived(
                conversationId,
                principal.getName(),
                value
        );

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{conversationId}/pin")
    public ResponseEntity<Void> pin(
            Principal principal,
            @PathVariable Long conversationId,
            @RequestParam boolean value
    ) {

        conversationService.setPinned(
                conversationId,
                principal.getName(),
                value
        );

        return ResponseEntity.noContent().build();
    }
}