package com.linkup.user.notification;
import com.linkup.user.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/moderation/announcements") @RequiredArgsConstructor
public class AnnouncementController {
    private final NotificationService notifications;
    private final UserRepository users;
    public record Announcement(@NotEmpty @Size(max = 100) List<@NotBlank String> recipients,
        @NotBlank @Size(max = 100) String title, @NotBlank @Size(max = 500) String body) {}
    @PostMapping @Transactional
    public void announce(@Valid @RequestBody Announcement body) {
        for (String id : body.recipients().stream().distinct().toList()) {
            var user = users.findByPublicId(id).orElseThrow(() -> new IllegalArgumentException("Recipient not found."));
            if (Boolean.TRUE.equals(user.getIsActive()) && !Boolean.TRUE.equals(user.getIsBanned()) && !Boolean.TRUE.equals(user.getIsDeleted()))
                notifications.create(user, null, NotificationType.SYSTEM, null, null, body.title(), body.body(), false);
        }
    }
}
