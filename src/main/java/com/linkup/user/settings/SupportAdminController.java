package com.linkup.user.settings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import com.linkup.user.exception.ResourceNotFoundException;
import java.time.Instant;

@RestController @RequestMapping("/api/moderation/support")
public class SupportAdminController {
    private final SupportTicketRepository tickets;
    private final com.linkup.user.notification.NotificationService notifications;
    private final com.linkup.user.repository.UserRepository users;
    public SupportAdminController(SupportTicketRepository tickets, com.linkup.user.notification.NotificationService notifications,
            com.linkup.user.repository.UserRepository users) { this.tickets=tickets; this.notifications=notifications; this.users=users; }
    public record Update(@NotBlank @Pattern(regexp="OPEN|IN_PROGRESS|RESOLVED") String status,@Size(max=2000) String reply) {}
    @GetMapping public Page<SupportTicket> list(@RequestParam(defaultValue="0") int page) {
        return tickets.findAll(PageRequest.of(Math.max(0,page),30,Sort.by("createdAt").descending()));
    }
    @PatchMapping("/{id}") @Transactional
    public SupportTicket update(@PathVariable Long id,@Valid @RequestBody Update value) {
        var ticket=tickets.findById(id).orElseThrow(()->new ResourceNotFoundException("Request not found."));
        String reply=value.reply()==null?null:value.reply().strip();
        boolean changed=!java.util.Objects.equals(ticket.status,value.status()) || !java.util.Objects.equals(ticket.reply,reply);
        ticket.status=value.status(); ticket.reply=reply; ticket.updatedAt=Instant.now();
        if (changed) users.findByPublicId(ticket.owner).ifPresent(owner -> notifications.create(owner, null,
            com.linkup.user.notification.NotificationType.SUPPORT_REPLY, ticket.id, null,
            reply != null && !reply.isBlank() ? "Support replied to your request" : "Your support request was updated",
            "Open request #" + ticket.id + " to see the latest update.", false));
        return tickets.save(ticket);
    }
}
