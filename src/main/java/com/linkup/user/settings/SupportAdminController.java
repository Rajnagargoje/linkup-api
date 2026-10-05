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
    public SupportAdminController(SupportTicketRepository tickets) { this.tickets=tickets; }
    public record Update(@NotBlank @Pattern(regexp="OPEN|IN_PROGRESS|RESOLVED") String status,@Size(max=2000) String reply) {}
    @GetMapping public Page<SupportTicket> list(@RequestParam(defaultValue="0") int page) {
        return tickets.findAll(PageRequest.of(Math.max(0,page),30,Sort.by("createdAt").descending()));
    }
    @PatchMapping("/{id}") @Transactional
    public SupportTicket update(@PathVariable Long id,@Valid @RequestBody Update value) {
        var ticket=tickets.findById(id).orElseThrow(()->new ResourceNotFoundException("Request not found."));
        ticket.status=value.status(); ticket.reply=value.reply()==null?null:value.reply().strip(); ticket.updatedAt=Instant.now();
        return tickets.save(ticket);
    }
}
