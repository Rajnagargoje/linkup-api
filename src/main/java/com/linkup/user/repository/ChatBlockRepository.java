package com.linkup.user.repository;

import com.linkup.user.entity.ChatBlock;
import org.springframework.data.jpa.repository.*;

public interface ChatBlockRepository extends JpaRepository<ChatBlock, Long> {
    boolean existsByBlockerAndTarget(String blocker, String target);
    @Query("select count(b) > 0 from ChatBlock b where (b.blocker = :a and b.target = :b) or (b.blocker = :b and b.target = :a)")
    boolean blocks(String a, String b);
    @Modifying @Query("update ChatBlock b set b.blocker = :account where b.blocker = :guest")
    void migrateBlocker(String guest, String account);
    @Modifying @Query("update ChatBlock b set b.target = :account where b.target = :guest")
    void migrateTarget(String guest, String account);
}
