package com.linkup.user.randomai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class AiCompanionStore {
    private final AiSavedConversationRepository repository;
    private final ObjectMapper json;
    public record Saved(String personaId, String name, String color, Instant updatedAt) {}
    public List<Saved> list(String owner) {
        return repository.findByOwnerPublicIdOrderByUpdatedAtDesc(owner).stream().map(row -> {
            AiPersona p = AiPersona.find(row.getPersona()); return new Saved(p.id(), p.name(), p.color(), row.getUpdatedAt());
        }).toList();
    }
    public List<AiTurn> load(String owner, String persona) {
        AiSavedConversation row = repository.findByOwnerPublicIdAndPersona(owner, persona)
            .orElseThrow(() -> new IllegalArgumentException("This saved chat is no longer available."));
        try { return json.readValue(row.getHistoryJson(), new TypeReference<List<AiTurn>>() {}); }
        catch (Exception ex) { throw new IllegalArgumentException("Could not load this conversation. Please try again."); }
    }
    @Transactional
    public void save(String owner, String persona, List<AiTurn> turns, boolean create) {
        AiSavedConversation row = repository.findByOwnerPublicIdAndPersona(owner, persona).orElse(null);
        if (row == null && !create) return; // A removed companion must not be recreated by a late reply.
        if (row == null) { row = new AiSavedConversation(); row.setId(UUID.randomUUID().toString()); row.setOwnerPublicId(owner); row.setPersona(persona); }
        try { row.setHistoryJson(json.writeValueAsString(turns.subList(Math.max(0, turns.size() - 80), turns.size()))); }
        catch (Exception ex) { throw new IllegalArgumentException("Could not save this conversation."); }
        row.setUpdatedAt(Instant.now()); repository.save(row);
    }
    @Transactional public void remove(String owner, String persona) { repository.deleteByOwnerPublicIdAndPersona(owner, persona); }
}
