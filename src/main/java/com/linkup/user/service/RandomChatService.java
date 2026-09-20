package com.linkup.user.service;

import com.linkup.user.utils.SimpleRateLimiter;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.*;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import java.security.Principal;
import java.time.Instant;
import java.util.*;

/** One shared, ephemeral queue for website guests and signed-in website/app users. */
@Service
public class RandomChatService {
    public record Preferences(String language, List<String> interests) {
        public Preferences {
            language = normalize(language);
            if (language.length() > 40) throw new IllegalArgumentException("Language is too long.");
            if (interests != null && interests.size() > 10) throw new IllegalArgumentException("Choose up to 10 interests.");
            interests = interests == null ? List.of() : interests.stream().filter(Objects::nonNull)
                    .map(Preferences::normalize).filter(s -> !s.isBlank()).distinct().toList();
            if (interests.stream().anyMatch(s -> s.length() > 40)) throw new IllegalArgumentException("Interests must be under 40 characters.");
        }
        private static String normalize(String text) { return text == null ? "" : text.strip().toLowerCase(Locale.ROOT); }
        int score(Preferences other) {
            int score = !language.isEmpty() && language.equals(other.language) ? 10 : 0;
            return score + (int) interests.stream().filter(other.interests::contains).count();
        }
    }
    private record Member(Principal principal, String sessionId, Preferences preferences) {}
    private record Match(String id, Member first, Member second) {
        Member other(Member member) { return first.sessionId().equals(member.sessionId()) ? second : first; }
    }
    private final SimpMessagingTemplate messaging;
    private final GuestSessionService identities;
    private final ChatRelationshipPolicy policy;
    private final ConnectionService connections;
    private final SimpleRateLimiter limiter;
    private final com.linkup.user.repository.ChatReportRepository reports;
    private final Map<String, Deque<String>> transcripts = new HashMap<>();
    private final Map<String, Member> members = new HashMap<>();
    private final Map<String, Member> waiting = new LinkedHashMap<>();
    private final Map<String, Match> matches = new HashMap<>();
    private final Map<String, String> recent = new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) { return size() > 10000; }
    };

    public RandomChatService(SimpMessagingTemplate messaging, GuestSessionService identities,
            ChatRelationshipPolicy policy, ConnectionService connections, SimpleRateLimiter limiter, com.linkup.user.repository.ChatReportRepository reports) {
        this.messaging = messaging; this.identities = identities; this.policy = policy;
        this.connections = connections; this.limiter = limiter; this.reports = reports;
    }
    public void join(String username, String sessionId) { join(() -> username, sessionId, new Preferences("", List.of())); }
    public synchronized void join(Principal principal, String sessionId, Preferences preferences) {
        Member member = new Member(principal, sessionId, preferences == null ? new Preferences("", List.of()) : preferences);
        var identity = identities.resolve(principal);
        for (Member old : new ArrayList<>(members.values())) {
            try { key(old); } catch (org.springframework.security.authentication.BadCredentialsException ex) { leave(old.sessionId()); }
        }
        if (members.values().stream().anyMatch(m -> !m.sessionId().equals(sessionId) && key(m).equals(identity.key()))) {
            error(member, "You already have a random chat open in another tab."); return;
        }
        if (matches.containsKey(sessionId) || waiting.containsKey(sessionId)) return;
        if (!limiter.tryConsume("random-join:" + identity.key(), 20, 60_000)) { error(member, "Please wait a minute before searching again."); return; }
        members.put(sessionId, member);
        Member partner = null;
        int bestScore = Integer.MIN_VALUE;
        for (Member candidate : new ArrayList<>(waiting.values())) {
            String candidateKey;
            try { candidateKey = key(candidate); }
            catch (org.springframework.security.authentication.BadCredentialsException e) { leave(candidate.sessionId()); continue; }
            if (!policy.mayMatch(identity.key(), candidateKey)) continue;
            int score = member.preferences().score(candidate.preferences());
            if (candidateKey.equals(recent.get(identity.key())) || identity.key().equals(recent.get(candidateKey))) score -= 100;
            if (score > bestScore) { partner = candidate; bestScore = score; }
        }
        if (partner == null) {
            waiting.put(sessionId, member); emit(member, Map.of("type", "WAITING"));
        } else {
            waiting.remove(partner.sessionId());
            Match match = new Match(UUID.randomUUID().toString(), partner, member);
            transcripts.put(match.id(), new ArrayDeque<>());
            matches.put(sessionId, match); matches.put(partner.sessionId(), match);
            emitMatch(member, match, "MATCHED"); emitMatch(partner, match, "MATCHED");
        }
    }
    public void message(String username, String sessionId, String matchId, String content) { message(() -> username, sessionId, matchId, content); }
    public synchronized void message(Principal principal, String sessionId, String matchId, String content) {
        Member member = owned(principal, sessionId, matchId);
        if (member == null) return;
        Match match = matches.get(sessionId);
        if (policy.blocked(key(member), key(match.other(member)))) { leave(sessionId); return; }
        if (content == null || content.isBlank() || content.length() > 2000) { error(member, "Messages must contain 1 to 2000 characters."); return; }
        if (!limiter.tryConsume("random-message:" + key(member), 60, 60_000)) { error(member, "You are sending messages too quickly. Please wait."); return; }
        var sender = identities.resolve(principal);
        Deque<String> transcript = transcripts.get(match.id());
        transcript.addLast(sender.key() + ": " + content.trim());
        while (transcript.size() > 20) transcript.removeFirst();
        Map<String, Object> event = Map.of("type", "MESSAGE", "matchId", match.id(), "id", UUID.randomUUID().toString(),
                "sender", sender.displayName(), "senderId", sender.key(), "content", content.trim(), "timeStamp", Instant.now().toString());
        emit(member, event); emit(match.other(member), event);
    }
    public synchronized void connect(Principal principal, String sessionId, String matchId) {
        Member member = owned(principal, sessionId, matchId);
        if (member == null) return;
        var self = identities.resolve(principal);
        Member partner = matches.get(sessionId).other(member);
        var other = identities.resolve(partner.principal());
        if (self.guest()) { emit(member, Map.of("type", "REGISTER_REQUIRED", "message", "Create an account to keep in touch. Your chat stays open.")); return; }
        if (other.guest()) {
            emit(member, Map.of("type", "CONNECTION", "message", "Your partner needs to register first. You can keep chatting."));
            emit(partner, Map.of("type", "REGISTER_REQUIRED", "message", "Your partner would like to keep in touch. Register, then send or accept a friend request."));
            return;
        }
        try {
            var status = connections.getConnectionStatus(self.username(), other.publicId());
            if (status.equals("NONE")) connections.sendRequest(self.username(), other.publicId());
            String message = status.equals("CONNECTED") ? "You are already friends." : status.equals("REQUEST_RECEIVED")
                    ? "Your partner sent a request. Accept it in your requests inbox." : "Friend request sent. Your partner can accept it in their requests inbox.";
            emit(member, Map.of("type", "CONNECTION", "message", message));
            emit(partner, Map.of("type", "CONNECTION", "message", "Check your requests inbox to keep in touch."));
        } catch (IllegalArgumentException e) { error(member, e.getMessage()); }
    }
    public synchronized void block(Principal principal, String sessionId, String matchId) {
        Member member = owned(principal, sessionId, matchId);
        if (member == null) return;
        policy.block(key(member), key(matches.get(sessionId).other(member)));
        leave(sessionId);
        emit(member, Map.of("type", "CONNECTION", "message", "Person blocked. You will not be matched with this identity again."));
    }
    public synchronized void report(Principal principal, String sessionId, String matchId, String reason) {
        Member member = owned(principal, sessionId, matchId);
        if (member == null) return;
        if (reason == null || reason.isBlank() || reason.length() > 500) { error(member, "Please provide a report reason under 500 characters."); return; }
        var report = new com.linkup.user.entity.ChatReport();
        report.setReporter(key(member)); report.setTarget(key(matches.get(sessionId).other(member)));
        report.setMatchId(matchId); report.setReason(reason.trim());
        report.setEvidence(String.join("\n", transcripts.getOrDefault(matchId, new ArrayDeque<>())));
        reports.save(report);
        block(principal, sessionId, matchId);
        emit(member, Map.of("type", "CONNECTION", "message", "Report submitted and person blocked."));
    }
    public synchronized void claimGuest(String token, String username) {
        var principal = identities.authenticate(token);
        var account = identities.resolve(() -> username);
        for (Member member : members.values()) {
            if (!member.principal().equals(principal) && key(member).equals(account.key()))
                throw new IllegalArgumentException("End your other random chat before linking this session.");
        }
        identities.claim(token, username);
        for (Member member : new ArrayList<>(members.values())) {
            if (!member.principal().equals(principal)) continue;
            Match match = matches.get(member.sessionId());
            if (match != null) {
                if (policy.blocked(key(member), key(match.other(member))) || key(member).equals(key(match.other(member)))) {
                    leave(member.sessionId());
                } else {
                    emitMatch(member, match, "MATCH_UPDATED"); emitMatch(match.other(member), match, "MATCH_UPDATED");
                }
            }
        }
    }
    public synchronized void leave(String sessionId) {
        Member member = members.remove(sessionId); waiting.remove(sessionId);
        Match match = matches.remove(sessionId);
        if (match != null && member != null) {
            transcripts.remove(match.id());
            Member partner = match.other(member);
            matches.remove(partner.sessionId()); members.remove(partner.sessionId());
            try { recent.put(key(member), key(partner)); recent.put(key(partner), key(member)); }
            catch (org.springframework.security.authentication.BadCredentialsException ignored) { /* Expired guest. */ }
            emit(partner, Map.of("type", "ENDED", "matchId", match.id(), "message", "Your partner left the chat."));
        }
        emitSession(sessionId, Map.of("type", "ENDED", "message", "Chat ended."));
    }
    @EventListener public void disconnected(SessionDisconnectEvent event) { leave(event.getSessionId()); }
    private String key(Member member) { return identities.resolve(member.principal()).key(); }
    private Member owned(Principal principal, String sessionId, String matchId) {
        Member member = members.get(sessionId); Match match = matches.get(sessionId);
        if (member == null || match == null || !match.id().equals(matchId) || !key(member).equals(identities.resolve(principal).key())) {
            emitSession(sessionId, Map.of("type", "ERROR", "message", "This chat has ended. Start a new chat.")); return null;
        }
        return member;
    }
    private void emitMatch(Member member, Match match, String type) {
        var self = identities.resolve(member.principal()); var other = identities.resolve(match.other(member).principal());
        emit(member, Map.of("type", type, "matchId", match.id(), "partner", other.displayName(), "partnerGuest", other.guest(),
                "selfId", self.key(), "partnerId", other.key()));
    }
    private void error(Member member, String message) { emit(member, Map.of("type", "ERROR", "message", message)); }
    private void emit(Member member, Map<String, ?> event) { emitSession(member.sessionId(), event); }
    private void emitSession(String sessionId, Map<String, ?> event) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId(sessionId); headers.setLeaveMutable(true);
        messaging.convertAndSendToUser(sessionId, "/queue/random", event, headers.getMessageHeaders());
    }
}
