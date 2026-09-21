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
    @Value("${linkup.push.enabled:false}") private boolean pushEnabled;
    public record ReadAll(@NotNull @PositiveOrZero Long throughId) {}
    public record Requests(@NotNull @Size(max = 100) List<@NotNull @Positive Long> connectionIds) {}
    public record Device(@NotBlank @Size(min = 20, max = 2048) String token) {}

    @GetMapping public Page<NotificationService.Item> list(Principal p, @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "false") boolean unread) { return service.list(p.getName(), page, unread); }
    @GetMapping("/counts") public NotificationService.Counts counts(Principal p) { return service.counts(p.getName()); }
    @GetMapping("/{id}") public NotificationService.Item get(Principal p, @PathVariable Long id) { return service.get(p.getName(), id); }
    @PatchMapping("/{id}/read") public void read(Principal p, @PathVariable Long id) { service.read(p.getName(), id); }
    @PatchMapping("/read-all") public void readAll(Principal p, @Valid @RequestBody ReadAll body) { service.readAll(p.getName(), body.throughId()); }
    @PatchMapping("/requests/read") public void requests(Principal p, @Valid @RequestBody Requests body) { service.readRequests(p.getName(), body.connectionIds()); }
    @GetMapping("/preferences") public NotificationPreferences preferences(Principal p) { return service.preferencesFor(service.user(p.getName()).getPublicId()); }
    @PutMapping("/preferences") public NotificationPreferences preferences(Principal p, @RequestBody NotificationPreferences body) { return service.savePreferences(p.getName(), body); }
    @GetMapping("/push-status") public Map<String, Boolean> status() { return Map.of("configured", pushEnabled); }
    @PostMapping("/devices") public void register(Principal p, @Valid @RequestBody Device body) { service.register(p.getName(), body.token()); }
    @DeleteMapping("/devices") public void unregister(Principal p, @Valid @RequestBody Device body) { service.unregister(p.getName(), body.token()); }
}
