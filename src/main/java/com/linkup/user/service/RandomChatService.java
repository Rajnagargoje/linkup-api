package com.linkup.user.service;

import com.linkup.user.randomai.*;
import com.linkup.user.utils.SimpleRateLimiter;
import jakarta.annotation.PreDestroy;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.*;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import java.security.Principal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** A session belongs to exactly one human match, AI chat, or waiting state. */
@Service
public class RandomChatService {
    public record Preferences(String language, List<String> interests, Boolean aiFallback) {
        public Preferences(String language, List<String> interests) { this(language, interests, false); }
        public Preferences {
            language = normalize(language);
            if (language.length() > 40) throw new IllegalArgumentException("Language is too long.");
            if (interests != null && interests.size() > 10) throw new IllegalArgumentException("Choose up to 10 interests.");
            interests = interests == null ? List.of() : interests.stream().filter(Objects::nonNull)
                .map(Preferences::normalize).filter(s -> !s.isBlank()).distinct().toList();
            if (interests.stream().anyMatch(s -> s.length() > 40)) throw new IllegalArgumentException("Interests must be under 40 characters.");
            // Old clients do not understand AI identity or handoff events: human matching only.
            aiFallback = Boolean.TRUE.equals(aiFallback);
        }
        private static String normalize(String text) { return text == null ? "" : text.strip().toLowerCase(Locale.ROOT); }
        int score(Preferences other) { return (!language.isEmpty() && language.equals(other.language) ? 10 : 0)
            + (int) interests.stream().filter(other.interests::contains).count(); }
    }
    private record Member(Principal principal, String sessionId, Preferences preferences) {}
    private record Match(String id, Member first, Member second) {
        Member other(Member member) { return first.sessionId().equals(member.sessionId()) ? second : first; }
    }
    private static class AiSession {
        final String id = UUID.randomUUID().toString();
        final AiPersona persona;
        final List<AiTurn> history = new ArrayList<>();
        AiChatClient.Pending pending;
        String generation;
        boolean saved, failed;
        AiSession(AiPersona persona) { this.persona = persona; }
    }
    private static class Offer {
        final String id = UUID.randomUUID().toString(); final Member first, second;
        final Set<String> accepted = new HashSet<>(); ScheduledFuture<?> timeout;
        Offer(Member first, Member second) { this.first = first; this.second = second; }
        List<Member> people() { return List.of(first, second); }
    }
    private final SimpMessagingTemplate messaging;
    private final GuestSessionService identities;
    private final ChatRelationshipPolicy policy;
    private final ConnectionService connections;
    private final SimpleRateLimiter limiter;
    private final com.linkup.user.repository.ChatReportRepository reports;
    private final com.linkup.user.notification.NotificationService notifications;
    private final AiChatClient aiClient;
    private final AiCompanionStore companions;
    private final ScheduledExecutorService timers = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "random-chat-timers"); thread.setDaemon(true); return thread;
    });
    private final Map<String, Deque<String>> transcripts = new HashMap<>();
    private final Map<String, Member> members = new HashMap<>();
    private final Map<String, Member> waiting = new LinkedHashMap<>();
    private final Map<String, Match> matches = new HashMap<>();
    private final Map<String, AiSession> aiSessions = new HashMap<>();
    private final Map<String, Offer> offers = new HashMap<>();
    private final Map<String, ScheduledFuture<?>> fallbacks = new HashMap<>();
    private final Map<String, Long> activity = new HashMap<>();
    private final Map<String, Map<String, Map<String, Object>>> receipts = new HashMap<>();
    private final Map<String, String> recent = new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) { return size() > 10000; }
    };
    public RandomChatService(SimpMessagingTemplate messaging, GuestSessionService identities,
            ChatRelationshipPolicy policy, ConnectionService connections, SimpleRateLimiter limiter,
            com.linkup.user.repository.ChatReportRepository reports,
            com.linkup.user.notification.NotificationService notifications, AiChatClient aiClient, AiCompanionStore companions) {
        this.messaging = messaging; this.identities = identities; this.policy = policy; this.connections = connections;
        this.limiter = limiter; this.reports = reports; this.notifications = notifications;
        this.aiClient = aiClient; this.companions = companions;
        timers.scheduleWithFixedDelay(this::expireIdle, 60, 60, TimeUnit.SECONDS);
    }
    public void join(String username, String sessionId) { join(() -> username, sessionId, new Preferences("", List.of())); }
    public synchronized void join(Principal principal, String sessionId, Preferences preferences) {
        if (members.containsKey(sessionId)) { verifyOwner(principal, sessionId); return; }
        Member member = addMember(principal, sessionId, preferences);
        if (member != null) enqueue(member);
    }
    private Member addMember(Principal principal, String sessionId, Preferences preferences) {
        var identity = identities.resolve(principal);
        for (Member old : new ArrayList<>(members.values())) {
            try { key(old); } catch (org.springframework.security.authentication.BadCredentialsException ex) { leave(old.sessionId()); }
        }
        Member member = new Member(principal, sessionId, preferences == null ? new Preferences("", List.of()) : preferences);
        if (members.values().stream().anyMatch(m -> !m.sessionId().equals(sessionId) && key(m).equals(identity.key()))) {
            error(member, "You already have a random chat open in another tab."); return null;
        }
        if (!limiter.tryConsume("random-join:" + identity.key(), 20, 60_000)) { error(member, "Please wait a minute before searching again."); return null; }
        members.put(sessionId, member); touch(member); return member;
    }
    private void enqueue(Member member) {
        waiting.put(member.sessionId(), member);
        Member partner = null; int bestScore = Integer.MIN_VALUE;
        for (Member candidate : new ArrayList<>(waiting.values())) {
            if (candidate.sessionId().equals(member.sessionId()) || offers.containsKey(candidate.sessionId())) continue;
            String candidateKey;
            try { candidateKey = key(candidate); }
            catch (org.springframework.security.authentication.BadCredentialsException e) { leave(candidate.sessionId()); continue; }
            if (!policy.mayMatch(key(member), candidateKey)) continue;
            int score = member.preferences().score(candidate.preferences());
            if (aiSessions.containsKey(candidate.sessionId())) score -= 5;
            if (candidateKey.equals(recent.get(key(member))) || key(member).equals(recent.get(candidateKey))) score -= 100;
            if (score > bestScore) { partner = candidate; bestScore = score; }
        }
        if (partner == null) {
            if (aiSessions.containsKey(member.sessionId())) emit(member, event("SEARCHING", "matchId", aiSessions.get(member.sessionId()).id, "searching", true));
            else {
                boolean enabled = member.preferences().aiFallback() && aiClient.available();
                emit(member, event("WAITING", "aiAvailable", enabled, "fallbackSeconds", aiClient.fallbackSeconds()));
                armFallback(member);
            }
        } else if (aiSessions.containsKey(member.sessionId()) || aiSessions.containsKey(partner.sessionId())) offer(member, partner);
        else pair(member, partner);
    }
    private void armFallback(Member member) {
        cancelFallback(member.sessionId());
        if (!member.preferences().aiFallback() || !aiClient.available()) return;
        fallbacks.put(member.sessionId(), timers.schedule(() -> fallback(member.sessionId()), aiClient.fallbackSeconds(), TimeUnit.SECONDS));
    }
    private synchronized void fallback(String sessionId) {
        fallbacks.remove(sessionId);
        Member member = waiting.get(sessionId);
        if (member == null || !member.preferences().aiFallback() || matches.containsKey(sessionId) || offers.containsKey(sessionId) || aiSessions.containsKey(sessionId)) return;
        try {
            identities.resolve(member.principal());
            if (!aiClient.available()) return;
            AiPersona persona = AiPersona.choose(member.preferences().interests(), recent.get("ai:" + key(member)));
            AiSession ai = new AiSession(persona);
            aiSessions.put(sessionId, ai); emitAiMatch(member, ai); // Keep the person in the human queue.
        } catch (RuntimeException ex) { error(member, "Could not start an AI chat. Still looking for a person."); }
    }
    private void pair(Member first, Member second) {
        for (Member m : List.of(first, second)) { waiting.remove(m.sessionId()); cancelFallback(m.sessionId()); stopAi(m.sessionId()); touch(m); }
        Match match = new Match(UUID.randomUUID().toString(), first, second);
        transcripts.put(match.id(), new ArrayDeque<>()); receipts.put(match.id(), new LinkedHashMap<>());
        matches.put(first.sessionId(), match); matches.put(second.sessionId(), match);
        emitMatch(first, match, "MATCHED"); emitMatch(second, match, "MATCHED");
    }
    private void offer(Member first, Member second) {
        Offer offer = new Offer(first, second);
        for (Member m : offer.people()) {
            waiting.remove(m.sessionId()); cancelFallback(m.sessionId()); offers.put(m.sessionId(), offer);
            AiSession ai = aiSessions.get(m.sessionId());
            if (ai == null) { offer.accepted.add(m.sessionId()); emit(m, event("WAITING", "aiAvailable", false, "message", "Someone is confirming your match…")); }
            else emit(m, event("HUMAN_OFFER", "matchId", ai.id, "offerId", offer.id, "expiresAt", Instant.now().plusSeconds(20).toString()));
        }
        offer.timeout = timers.schedule(() -> expireOffer(offer), 20, TimeUnit.SECONDS);
    }
    private synchronized void expireOffer(Offer offer) {
        if (offers.get(offer.first.sessionId()) == offer) releaseOffer(offer, null);
    }
    public synchronized void respondOffer(Principal principal, String sessionId, String offerId, boolean accept) {
        Member member = verifyOwner(principal, sessionId); Offer offer = offers.get(sessionId);
        if (member == null || offer == null || !offer.id.equals(offerId)) return;
        touch(member);
        if (!accept) { releaseOffer(offer, null); return; }
        offer.accepted.add(sessionId);
        emit(member, event("OFFER_ACCEPTED", "offerId", offer.id));
        if (offer.accepted.size() < 2) return;
        for (Member m : offer.people()) offers.remove(m.sessionId());
        if (offer.timeout != null) offer.timeout.cancel(false);
        if (!policy.mayMatch(key(offer.first), key(offer.second))) { releaseOffer(offer, null); return; }
        pair(offer.first, offer.second);
    }
    private void releaseOffer(Offer offer, String leaving) {
        if (offer.timeout != null) offer.timeout.cancel(false);
        for (Member m : offer.people()) offers.remove(m.sessionId());
        for (Member m : offer.people()) {
            if (m.sessionId().equals(leaving) || !members.containsKey(m.sessionId())) continue;
            AiSession ai = aiSessions.get(m.sessionId());
            if (ai != null) emit(m, event("OFFER_ENDED", "matchId", ai.id, "offerId", offer.id, "searching", false));
            else enqueue(m);
        }
    }
    public synchronized void searchPeople(Principal principal, String sessionId, String matchId, boolean search) {
        Member member = owned(principal, sessionId, matchId); if (member == null || !aiSessions.containsKey(sessionId)) return;
        if (offers.containsKey(sessionId)) return;
        touch(member);
        if (search) enqueue(member);
        else { waiting.remove(sessionId); emit(member, event("SEARCHING", "matchId", matchId, "searching", false)); }
    }
    public void message(String username, String sessionId, String matchId, String content) { message(() -> username, sessionId, matchId, content, null); }
    public void message(Principal principal, String sessionId, String matchId, String content) { message(principal, sessionId, matchId, content, null); }
    public synchronized void message(Principal principal, String sessionId, String matchId, String content, String clientId) {
        Member member = owned(principal, sessionId, matchId); if (member == null) return;
        if (content == null || content.isBlank() || content.length() > 2000 || (clientId != null && !clientId.matches("[A-Za-z0-9_-]{1,80}"))) {
            error(member, "Messages must contain 1 to 2000 characters.", matchId, clientId); return;
        }
        var sender = identities.resolve(principal); AiSession ai = aiSessions.get(sessionId);
        if (ai != null) {
            if (clientId != null) for (AiTurn turn : ai.history) if (clientId.equals(turn.clientId()) && !turn.assistant()) { emitTurn(member, ai.id, turn); return; }
            if (ai.pending != null) { error(member, "Wait for the current reply before sending another message.", matchId, clientId); return; }
            if (!limiter.tryConsume("random-ai-message:" + sender.key(), 12, 60_000)) { error(member, "Please slow down and try again in a moment.", matchId, clientId); return; }
            AiTurn turn = AiTurn.create(sender.key(), sender.displayName(), content.strip(), clientId);
            ai.history.add(turn); trim(ai); ai.failed = false; touch(member); emitTurn(member, ai.id, turn); persist(member, ai);
            reply(member, ai); return;
        }
        Match match = matches.get(sessionId);
        if (policy.blocked(sender.key(), key(match.other(member)))) { leave(sessionId); return; }
        String receiptKey = sender.key() + ":" + clientId;
        Map<String, Map<String, Object>> cache = receipts.get(matchId);
        if (clientId != null && cache.containsKey(receiptKey)) { emit(member, cache.get(receiptKey)); return; }
        if (!limiter.tryConsume("random-message:" + sender.key(), 60, 60_000)) { error(member, "You are sending messages too quickly. Please wait.", matchId, clientId); return; }
        Deque<String> transcript = transcripts.get(match.id()); transcript.addLast(sender.key() + ": " + content.strip());
        while (transcript.size() > 20) transcript.removeFirst();
        Map<String, Object> event = event("MESSAGE", "matchId", match.id(), "id", UUID.randomUUID().toString(), "sender", sender.displayName(),
            "senderId", sender.key(), "content", content.strip(), "timeStamp", Instant.now().toString(), "clientId", clientId);
        if (clientId != null) { cache.put(receiptKey, event); if (cache.size() > 200) cache.remove(cache.keySet().iterator().next()); }
        touch(member); emit(member, event); emit(match.other(member), event);
    }
    private void reply(Member member, AiSession ai) {
        String generation = UUID.randomUUID().toString(); ai.generation = generation; ai.failed = false;
        try {
            ai.pending = aiClient.reply(key(member), ai.persona, member.preferences().language(), member.preferences().interests(), List.copyOf(ai.history));
            emit(member, event("TYPING", "matchId", ai.id, "typing", true));
            ai.pending.result().whenComplete((text, failure) -> completeReply(member, ai, generation, text, failure));
        } catch (RuntimeException ex) { ai.pending = null; ai.failed = true;
            emit(member, event("AI_ERROR", "matchId", ai.id, "message", ex instanceof IllegalArgumentException ? ex.getMessage() : "Could not reply. Please try again.")); }
    }
    private synchronized void completeReply(Member member, AiSession ai, String generation, String text, Throwable failure) {
        if (aiSessions.get(member.sessionId()) != ai || !generation.equals(ai.generation)) return;
        ai.pending = null; emit(member, event("TYPING", "matchId", ai.id, "typing", false));
        try { identities.resolve(member.principal()); } catch (RuntimeException ex) { leave(member.sessionId()); return; }
        if (failure != null) { ai.failed = true; emit(member, event("AI_ERROR", "matchId", ai.id, "message", "Could not get a reply. Try again, or keep looking for a person.")); return; }
        AiTurn answer = AiTurn.create("ai:" + ai.persona.id(), ai.persona.name(), text, null);
        ai.history.add(answer); trim(ai); emitTurn(member, ai.id, answer); persist(member, ai);
    }
    public synchronized void retryAi(Principal principal, String sessionId, String matchId) {
        Member member = owned(principal, sessionId, matchId); AiSession ai = aiSessions.get(sessionId);
        if (member == null || ai == null || !ai.failed || ai.pending != null) return;
        if (!limiter.tryConsume("random-ai-message:" + key(member), 12, 60_000)) { error(member, "Please wait a moment before retrying."); return; }
        touch(member); reply(member, ai);
    }
    public synchronized void typing(Principal principal, String sessionId, String matchId, boolean typing) {
        Member member = owned(principal, sessionId, matchId); Match match = matches.get(sessionId);
        if (member == null || match == null) return;
        if (!limiter.tryConsume("random-typing:" + key(member), 40, 60_000)) return;
        emit(match.other(member), event("TYPING", "matchId", matchId, "typing", typing));
    }
    public synchronized void connect(Principal principal, String sessionId, String matchId) {
        Member member = owned(principal, sessionId, matchId); if (member == null) return;
        if (aiSessions.containsKey(sessionId)) { error(member, "Use Save chat to return to this AI conversation."); return; }
        var self = identities.resolve(principal); Member partner = matches.get(sessionId).other(member); var other = identities.resolve(partner.principal());
        if (self.guest()) { emit(member, event("REGISTER_REQUIRED", "message", "Create an account to keep in touch. Your chat stays open.")); return; }
        if (other.guest()) {
            emit(member, event("CONNECTION", "matchId", matchId, "message", "Your partner needs to register first. You can keep chatting."));
            emit(partner, event("REGISTER_REQUIRED", "message", "Your partner would like to keep in touch. Register to send or accept a friend request.")); return;
        }
        try {
            String status = connections.getConnectionStatus(self.username(), other.publicId());
            if ("NONE".equals(status)) connections.sendRequest(self.username(), other.publicId());
            relationship(member); relationship(partner);
        } catch (IllegalArgumentException ex) { error(member, ex.getMessage()); }
    }
    public synchronized void acceptConnection(Principal principal, String sessionId, String matchId) {
        Member member = owned(principal, sessionId, matchId); if (member == null || !matches.containsKey(sessionId)) return;
        var self = identities.resolve(principal); Member partner = matches.get(sessionId).other(member); var other = identities.resolve(partner.principal());
        if (self.guest() || other.guest()) { error(member, "Both people need an account to become friends."); return; }
        var request = connections.getReceivedRequests(self.username()).stream().filter(r -> other.publicId().equals(r.getUserId())).findFirst();
        if (request.isPresent()) connections.acceptRequest(self.username(), request.get().getConnectionId());
        relationship(member); relationship(partner);
    }
    public synchronized void relationship(Principal principal, String sessionId, String matchId) {
        Member member = owned(principal, sessionId, matchId); if (member != null && matches.containsKey(sessionId)) relationship(member);
    }
    private void relationship(Member member) {
        Match match = matches.get(member.sessionId()); var self = identities.resolve(member.principal()); var other = identities.resolve(match.other(member).principal());
        if (self.guest() || other.guest()) return;
        String status = connections.getConnectionStatus(self.username(), other.publicId());
        emit(member, event("CONNECTION", "matchId", match.id(), "connectionStatus", status == null ? "NONE" : status,
            "message", "CONNECTED".equals(status) ? "You're friends! Find this person in Messages." : "REQUEST_SENT".equals(status) ? "Friend request sent." : "REQUEST_RECEIVED".equals(status) ? "Your partner wants to keep in touch." : ""));
    }
    public synchronized void companions(Principal principal, String sessionId) {
        var identity = identities.resolve(principal);
        emitSession(sessionId, event("COMPANIONS", "companions", identity.guest() ? List.of() : companions.list(identity.publicId()), "aiAvailable", aiClient.available(), "fallbackSeconds", aiClient.fallbackSeconds()));
    }
    public synchronized void saveCompanion(Principal principal, String sessionId, String matchId) {
        Member member = owned(principal, sessionId, matchId); AiSession ai = aiSessions.get(sessionId); if (member == null || ai == null) return;
        var self = identities.resolve(principal);
        if (self.guest()) { emit(member, event("REGISTER_REQUIRED", "message", "Create an account to save this chat.")); return; }
        companions.save(self.publicId(), ai.persona.id(), List.copyOf(ai.history), true); ai.saved = true;
        emit(member, event("COMPANION_SAVED", "matchId", ai.id, "saved", true)); companions(principal, sessionId);
    }
    public synchronized void removeCompanion(Principal principal, String sessionId, String personaId) {
        var self = identities.resolve(principal); if (self.guest()) return; AiPersona.find(personaId);
        companions.remove(self.publicId(), personaId);
        AiSession ai = aiSessions.get(sessionId);
        if (ai != null && ai.persona.id().equals(personaId)) { ai.saved = false; emitSession(sessionId, event("COMPANION_SAVED", "matchId", ai.id, "saved", false)); }
        companions(principal, sessionId);
    }
    public synchronized void resumeCompanion(Principal principal, String sessionId, String personaId, Preferences preferences) {
        var self = identities.resolve(principal);
        if (self.guest()) { emitSession(sessionId, event("REGISTER_REQUIRED", "message", "Sign in to open saved chats.")); return; }
        if (members.containsKey(sessionId)) { error(members.get(sessionId), "End your current chat before opening a saved chat."); return; }
        if (!aiClient.available()) { emitSession(sessionId, event("ERROR", "message", "AI chat is temporarily unavailable. You can still meet people.")); return; }
        AiPersona persona = AiPersona.find(personaId); List<AiTurn> history = companions.load(self.publicId(), personaId);
        Member member = addMember(principal, sessionId, preferences); if (member == null) return;
        AiSession ai = new AiSession(persona); ai.history.addAll(history); ai.saved = true;
        ai.failed = !history.isEmpty() && !history.get(history.size() - 1).assistant();
        aiSessions.put(sessionId, ai); emitAiMatch(member, ai);
    }
    private void persist(Member member, AiSession ai) {
        if (!ai.saved) return;
        try { var self = identities.resolve(member.principal()); if (!self.guest()) companions.save(self.publicId(), ai.persona.id(), List.copyOf(ai.history), false); }
        catch (RuntimeException ex) { emit(member, event("NOTICE", "matchId", ai.id, "message", "This chat could not be saved. Tap Save chat to retry.")); ai.saved = false; emit(member, event("COMPANION_SAVED", "matchId", ai.id, "saved", false)); }
    }
    private void trim(AiSession ai) { while (ai.history.size() > 80) ai.history.remove(0); }
    public synchronized void block(Principal principal, String sessionId, String matchId) {
        Member member = owned(principal, sessionId, matchId); if (member == null) return;
        if (aiSessions.containsKey(sessionId)) { leave(sessionId); return; }
        policy.block(key(member), key(matches.get(sessionId).other(member))); leave(sessionId);
        emit(member, event("CONNECTION", "message", "Person blocked. You will not be matched with this identity again."));
    }
    public synchronized void report(Principal principal, String sessionId, String matchId, String reason) {
        Member member = owned(principal, sessionId, matchId); if (member == null) return;
        if (reason == null || reason.isBlank() || reason.length() > 500) { error(member, "Please provide a report reason under 500 characters."); return; }
        if (!limiter.tryConsume("random-report:" + key(member), 5, 60_000)) { error(member, "Please wait before sending another report."); return; }
        AiSession ai = aiSessions.get(sessionId); var report = new com.linkup.user.entity.ChatReport();
        report.setReporter(key(member)); report.setTarget(ai == null ? key(matches.get(sessionId).other(member)) : "ai:" + ai.persona.id());
        report.setMatchId(matchId); report.setReason(reason.strip());
        report.setEvidence(ai == null ? String.join("\n", transcripts.getOrDefault(matchId, new ArrayDeque<>())) :
            String.join("\n", ai.history.stream().skip(Math.max(0, ai.history.size() - 20)).map(t -> t.senderId() + ": " + t.content()).toList()));
        reports.save(report); notifications.reportReceived(report.getReporter(), report.getId());
        if (ai == null) block(principal, sessionId, matchId); else leave(sessionId);
        emit(member, event("CONNECTION", "message", ai == null ? "Report submitted and person blocked." : "AI response reported. Chat ended."));
    }
    public synchronized void claimGuest(String token, String username) {
        var principal = identities.authenticate(token); var account = identities.resolve(() -> username);
        for (Member member : members.values()) if (!member.principal().equals(principal) && key(member).equals(account.key()))
            throw new IllegalArgumentException("End your other random chat before linking this session.");
        identities.claim(token, username);
        for (Member member : new ArrayList<>(members.values())) {
            if (!member.principal().equals(principal)) continue;
            Match match = matches.get(member.sessionId());
            if (match != null) {
                if (policy.blocked(key(member), key(match.other(member))) || key(member).equals(key(match.other(member)))) leave(member.sessionId());
                else { emitMatch(member, match, "MATCH_UPDATED"); emitMatch(match.other(member), match, "MATCH_UPDATED"); }
            } else if (aiSessions.containsKey(member.sessionId())) {
                AiSession ai = aiSessions.get(member.sessionId());
                for (int i = 0; i < ai.history.size(); i++) { AiTurn t = ai.history.get(i); if (!t.assistant()) ai.history.set(i, new AiTurn(t.id(), account.key(), account.displayName(), t.content(), t.timeStamp(), t.clientId())); }
                emitAiMatch(member, ai);
            }
        }
    }
    public synchronized void leave(String sessionId) {
        Member member = members.get(sessionId); waiting.remove(sessionId); activity.remove(sessionId); cancelFallback(sessionId);
        Offer offer = offers.get(sessionId); if (offer != null) releaseOffer(offer, sessionId);
        stopAi(sessionId);
        members.remove(sessionId);
        Match match = matches.remove(sessionId);
        if (match != null && member != null) {
            transcripts.remove(match.id()); receipts.remove(match.id()); Member partner = match.other(member);
            matches.remove(partner.sessionId()); members.remove(partner.sessionId()); activity.remove(partner.sessionId());
            try { recent.put(key(member), key(partner)); recent.put(key(partner), key(member)); }
            catch (org.springframework.security.authentication.BadCredentialsException ignored) { }
            emit(partner, event("ENDED", "matchId", match.id(), "message", "Your partner left the chat."));
        }
        emitSession(sessionId, event("ENDED", "message", "Chat ended."));
    }
    private void stopAi(String sessionId) {
        AiSession ai = aiSessions.remove(sessionId); if (ai == null) return;
        Member member = members.get(sessionId);
        if (member != null) { try { recent.put("ai:" + key(member), ai.persona.id()); } catch (RuntimeException ignored) { } }
        ai.generation = null; if (ai.pending != null) ai.pending.cancel().run();
    }
    private void cancelFallback(String sessionId) { ScheduledFuture<?> future = fallbacks.remove(sessionId); if (future != null) future.cancel(false); }
    private void touch(Member member) { activity.put(member.sessionId(), System.currentTimeMillis()); }
    private synchronized void expireIdle() {
        try { for (var entry : new ArrayList<>(activity.entrySet())) if (System.currentTimeMillis() - entry.getValue() > 30 * 60_000) leave(entry.getKey()); }
        catch (RuntimeException ignored) { /* A transient broker failure must not stop future cleanup. */ }
    }
    @EventListener public void disconnected(SessionDisconnectEvent event) { leave(event.getSessionId()); }
    @PreDestroy public synchronized void shutdown() { timers.shutdownNow(); for (String session : new ArrayList<>(aiSessions.keySet())) stopAi(session); }
    private String key(Member member) { return identities.resolve(member.principal()).key(); }
    private Member verifyOwner(Principal principal, String sessionId) {
        Member member = members.get(sessionId);
        if (member == null || !key(member).equals(identities.resolve(principal).key())) { emitSession(sessionId, event("ERROR", "message", "This chat has ended. Start a new chat.")); return null; }
        return member;
    }
    private Member owned(Principal principal, String sessionId, String matchId) {
        Member member = verifyOwner(principal, sessionId); if (member == null) return null;
        Match match = matches.get(sessionId); AiSession ai = aiSessions.get(sessionId);
        if ((match != null && match.id().equals(matchId)) || (ai != null && ai.id.equals(matchId))) return member;
        error(member, "This chat has ended. Start a new chat.", matchId, null); return null;
    }
    private void emitMatch(Member member, Match match, String type) {
        var self = identities.resolve(member.principal()); var other = identities.resolve(match.other(member).principal());
        emit(member, event(type, "matchId", match.id(), "partner", other.displayName(), "partnerKind", "HUMAN", "partnerGuest", other.guest(),
            "selfId", self.key(), "partnerId", other.key(), "partnerPublicId", other.publicId(),
            "sharedInterests", member.preferences().interests().stream().filter(match.other(member).preferences().interests()::contains).toList()));
    }
    private void emitAiMatch(Member member, AiSession ai) {
        emit(member, event("MATCHED", "matchId", ai.id, "partner", ai.persona.name(), "partnerKind", "AI", "partnerId", "ai:" + ai.persona.id(),
            "personaId", ai.persona.id(), "personaColor", ai.persona.color(), "personaProfile", ai.persona.profileLabel(), "selfId", key(member), "saved", ai.saved,
            "messages", List.copyOf(ai.history), "searching", waiting.containsKey(member.sessionId()), "retryReply", ai.failed));
    }
    private void emitTurn(Member member, String matchId, AiTurn turn) {
        emit(member, event("MESSAGE", "matchId", matchId, "id", turn.id(), "senderId", turn.senderId(), "sender", turn.sender(),
            "content", turn.content(), "timeStamp", turn.timeStamp(), "clientId", turn.clientId()));
    }
    private void error(Member member, String message) { error(member, message, null, null); }
    private void error(Member member, String message, String matchId, String clientId) { emit(member, event("ERROR", "message", message, "matchId", matchId, "clientId", clientId)); }
    private static Map<String, Object> event(String type, Object... values) {
        Map<String, Object> payload = new LinkedHashMap<>(); payload.put("type", type);
        for (int i = 0; i < values.length; i += 2) if (values[i + 1] != null) payload.put((String) values[i], values[i + 1]);
        return payload;
    }
    private void emit(Member member, Map<String, ?> event) { emitSession(member.sessionId(), event); }
    private void emitSession(String sessionId, Map<String, ?> event) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId(sessionId); headers.setLeaveMutable(true);
        messaging.convertAndSendToUser(sessionId, "/queue/random", event, headers.getMessageHeaders());
    }
}
