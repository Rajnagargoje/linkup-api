package com.linkup.user.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api/notifications") @RequiredArgsConstructor
public class NotificationController {
    private final NotificationService service;
    private final com.linkup.user.service.impl.JWTService jwt;
    private final org.springframework.beans.factory.ObjectProvider<com.google.firebase.messaging.FirebaseMessaging> firebase;
    @Value("${linkup.push.enabled:false}") private boolean pushEnabled;
    public record ReadAll(@NotNull @PositiveOrZero Long throughId) {}
    public record Requests(@NotNull @Size(max = 100) List<@NotNull @Positive Long> connectionIds) {}
    public record Device(@NotBlank @Size(min = 20, max = 2048) String token,
        @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String installationId) {}
    public record Delivered(@NotNull @Size(max = 100) List<@NotNull @Positive Long> ids) {}
    public record Preferences(Boolean pushEnabled, Boolean messages, Boolean friendRequests, Boolean updates,
        Boolean messagePreview, Boolean supportReplies) {}

    @GetMapping public Page<NotificationService.Item> list(Principal p, @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "false") boolean unread) { return service.list(p.getName(), page, unread); }
    @GetMapping("/counts") public NotificationService.Counts counts(Principal p) { return service.counts(p.getName()); }
    @GetMapping("/{id}") public NotificationService.Item get(Principal p, @PathVariable Long id) { return service.get(p.getName(), id); }
    @PatchMapping("/{id}/read") public void read(Principal p, @PathVariable Long id) { service.read(p.getName(), id); }
    @PatchMapping("/read-all") public void readAll(Principal p, @Valid @RequestBody ReadAll body) { service.readAll(p.getName(), body.throughId()); }
    @PatchMapping("/requests/read") public void requests(Principal p, @Valid @RequestBody Requests body) { service.readRequests(p.getName(), body.connectionIds()); }
    @GetMapping("/preferences") public NotificationPreferences preferences(Principal p) { return service.preferencesFor(service.user(p.getName()).getPublicId()); }
    @PutMapping("/preferences") public NotificationPreferences preferences(Principal p, @RequestBody Preferences body) {
        var saved = service.preferencesFor(service.user(p.getName()).getPublicId());
        if (body.pushEnabled() != null) saved.setPushEnabled(body.pushEnabled());
        if (body.messages() != null) saved.setMessages(body.messages());
        if (body.friendRequests() != null) saved.setFriendRequests(body.friendRequests());
        if (body.updates() != null) saved.setUpdates(body.updates());
        if (body.messagePreview() != null) saved.setMessagePreview(body.messagePreview());
        if (body.supportReplies() != null) saved.setSupportReplies(body.supportReplies());
        return service.savePreferences(p.getName(), saved);
    }
    @GetMapping("/push-status") public Map<String, Boolean> status() { return Map.of("configured", pushEnabled && firebase.getIfAvailable() != null); }
    @PostMapping("/delivered-state") public List<Long> delivered(Principal p, @Valid @RequestBody Delivered body) { return service.activeDeliveredIds(p.getName(), body.ids()); }
    @PostMapping("/devices") public void register(Principal p, @RequestHeader("Authorization") String authorization, @Valid @RequestBody Device body) {
        service.register(p.getName(), body.token(), body.installationId(), jwt.extractSessionId(authorization.substring(7)));
    }
    @DeleteMapping("/devices") public void unregister(Principal p, @Valid @RequestBody Device body) { service.unregister(p.getName(), body.token()); }
}
