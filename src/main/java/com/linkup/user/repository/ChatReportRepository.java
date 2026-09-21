package com.linkup.user.repository;
import com.linkup.user.entity.ChatReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
public interface ChatReportRepository extends JpaRepository<ChatReport, Long> {
    boolean existsByReporterAndTarget(String reporter, String target);
    @Modifying @Query("update ChatReport r set r.reporter = :account where r.reporter = :guest")
    void migrateReporter(String guest, String account);
    @Modifying @Query("update ChatReport r set r.target = :account where r.target = :guest")
    void migrateTarget(String guest, String account);
}
