package com.linkup.user.settings;

import com.linkup.user.entity.User;
import com.linkup.user.repository.UserRepository;
import com.linkup.user.repository.ChatBlockRepository;
import com.linkup.user.exception.ResourceNotFoundException;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AccountSettingsService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AccountSettingsService.class);
    private final UserRepository users;
    private final ChatBlockRepository blocks;
    private final SupportTicketRepository tickets;
    private final SimpMessagingTemplate messaging;
    public AccountSettingsService(UserRepository users, ChatBlockRepository blocks,
            SupportTicketRepository tickets, SimpMessagingTemplate messaging) {
        this.users = users; this.blocks = blocks; this.tickets = tickets; this.messaging = messaging;
    }
    public record Privacy(@NotNull Boolean discoverable, @NotNull Boolean activityVisible,
                          @NotNull Boolean messageRequestsEnabled) {}
    public record BlockedUser(Long id, String publicId, String username, String profilePhoto, Instant blockedAt) {}
    public record PageResult<T>(List<T> items, Integer nextPage) {}
    public record SupportRequest(@NotBlank @Pattern(regexp="GENERAL|BUG|SAFETY|PRIVACY") String category,
            @NotBlank @Size(max=120) String subject, @NotBlank @Size(min=10,max=2000) String message) {}
    public record TicketView(Long id, String category, String subject, String message,
            String status, String reply, Instant createdAt) {}

    private User user(String username) {
        return users.findByUsername(username)
            .filter(u -> Boolean.TRUE.equals(u.getIsActive()) && !Boolean.TRUE.equals(u.getIsDeleted()))
            .orElseThrow(() -> new ResourceNotFoundException("Account unavailable."));
    }
    private Privacy privacy(User user) {
        return new Privacy(Boolean.TRUE.equals(user.getLocationVisible()),
            !Boolean.FALSE.equals(user.getActivityVisible()), !Boolean.FALSE.equals(user.getMessageRequestsEnabled()));
    }
    @Transactional(readOnly=true)
    public Privacy privacy(String username) { return privacy(user(username)); }

    @Transactional
    public Privacy savePrivacy(String username, Privacy request) {
        User owner = users.findByUsernameForUpdate(username).orElseThrow(() -> new ResourceNotFoundException("Account unavailable."));
        owner.setLocationVisible(request.discoverable());
        owner.setActivityVisible(request.activityVisible());
        owner.setMessageRequestsEnabled(request.messageRequestsEnabled());
        users.save(owner);
        // Publish only the public presence state, never the hidden online state.
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit() {
                    try {
                        messaging.convertAndSend("/topic/presence", Map.of("username", owner.getUsername(),
                            "status", owner.getPublicOnline() ? "ONLINE" : "OFFLINE"));
                    } catch (RuntimeException failure) {
                        // The preference is already committed; a broker failure must not report a failed save.
                        log.warn("Privacy saved, but the presence update could not be delivered.", failure);
                    }
                }
            });
        return privacy(owner);
    }
    @Transactional(readOnly=true)
    public PageResult<BlockedUser> blocked(String username, int page) {
        User owner = user(username);
        var records = blocks.findByBlockerOrderByCreatedAtDesc("u:" + owner.getPublicId(), PageRequest.of(Math.max(0,page),30));
        var ids = records.stream().map(b -> b.getTarget()).filter(t -> t.startsWith("u:")).map(t -> t.substring(2)).toList();
        var targets = ids.isEmpty() ? Map.<String,User>of() : users.findByPublicIdIn(ids).stream()
            .collect(Collectors.toMap(User::getPublicId, u -> u));
        var result = records.stream().map(b -> {
            User target = b.getTarget().startsWith("u:") ? targets.get(b.getTarget().substring(2)) : null;
            boolean visible = target != null && !Boolean.TRUE.equals(target.getIsDeleted());
            return new BlockedUser(b.getId(), visible ? target.getPublicId() : null,
                visible ? target.getUsername() : b.getTarget().startsWith("g:") ? "Guest participant" : "Unavailable account",
                visible ? target.getProfilePhoto() : null, b.getCreatedAt());
        }).toList();
        return new PageResult<>(result, records.hasNext() ? records.getNumber()+1 : null);
    }
    @Transactional
    public void unblock(String username, Long id) {
        String owner = "u:" + user(username).getPublicId();
        var record = blocks.findByIdAndBlocker(id, owner)
            .orElseThrow(() -> new ResourceNotFoundException("This block was not found."));
        blocks.deleteByBlockerAndTarget(owner, record.getTarget());
        // Friendship, reports, and any block in the opposite direction stay intact.
    }
    @Transactional
    public TicketView createTicket(String username, SupportRequest request) {
        User owner = users.findByUsernameForUpdate(username).orElseThrow();
        if (tickets.countByOwnerAndCreatedAtAfter(owner.getPublicId(), Instant.now().minus(1,ChronoUnit.HOURS)) >= 5)
            throw new IllegalArgumentException("You have sent several requests. Please try again in an hour.");
        if (request.subject().strip().isEmpty() || request.message().strip().length()<10)
            throw new IllegalArgumentException("Add a subject and at least 10 characters describing your request.");
        SupportTicket ticket = new SupportTicket(); ticket.owner=owner.getPublicId();
        ticket.category=request.category(); ticket.subject=request.subject().strip(); ticket.message=request.message().strip();
        return view(tickets.save(ticket));
    }
    @Transactional(readOnly=true)
    public PageResult<TicketView> tickets(String username, int page) {
        var results=tickets.findByOwnerOrderByCreatedAtDesc(user(username).getPublicId(),PageRequest.of(Math.max(0,page),20));
        return new PageResult<>(results.map(this::view).getContent(),results.hasNext()?results.getNumber()+1:null);
    }
    public TicketView view(SupportTicket ticket) {
        return new TicketView(ticket.id,ticket.category,ticket.subject,ticket.message,ticket.status,ticket.reply,ticket.createdAt);
    }
    @Transactional(readOnly=true)
    public TicketView ticket(String username, Long id) {
        return view(tickets.findByIdAndOwner(id, user(username).getPublicId())
            .orElseThrow(() -> new ResourceNotFoundException("Request not found.")));
    }
}
