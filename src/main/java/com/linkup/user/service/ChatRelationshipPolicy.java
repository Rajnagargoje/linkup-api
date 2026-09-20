package com.linkup.user.service;

import com.linkup.user.entity.ChatBlock;
import com.linkup.user.entity.User;
import com.linkup.user.repository.ChatBlockRepository;
import com.linkup.user.repository.ConnectionRepository;
import com.linkup.user.utils.ConnectionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class ChatRelationshipPolicy {
    private final ChatBlockRepository blocks;
    private final ConnectionRepository connections;

    public boolean blocked(String a, String b) { return blocks.blocks(a, b); }
    public boolean friends(String a, String b) {
        if (!a.startsWith("u:") || !b.startsWith("u:")) return false;
        String left = a.substring(2), right = b.substring(2);
        String pair = left.compareTo(right) < 0 ? left + ":" + right : right + ":" + left;
        return connections.findByPairKey(pair).map(c -> c.getStatus() == ConnectionStatus.ACCEPTED).orElse(false);
    }
    public boolean mayMatch(String a, String b) { return !a.equals(b) && !blocked(a, b) && !friends(a, b); }
    public void ensureContact(User a, User b) {
        if (blocked("u:" + a.getPublicId(), "u:" + b.getPublicId())
                || Boolean.TRUE.equals(b.getIsBanned()) || Boolean.TRUE.equals(b.getIsDeleted())
                || !Boolean.TRUE.equals(b.getIsActive())) throw new IllegalArgumentException("This person is unavailable.");
    }
    @Transactional
    public void block(String a, String b) {
        if (a.equals(b)) throw new IllegalArgumentException("You cannot block yourself.");
        if (!blocks.existsByBlockerAndTarget(a, b)) {
            ChatBlock block = new ChatBlock(); block.setBlocker(a); block.setTarget(b); blocks.save(block);
        }
    }
    @Transactional
    public void migrateGuest(String guest, String account) {
        blocks.migrateBlocker(guest, account);
        blocks.migrateTarget(guest, account);
    }
}
