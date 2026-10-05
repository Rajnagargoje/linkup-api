package com.linkup.user.settings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    Page<SupportTicket> findByOwnerOrderByCreatedAtDesc(String owner, Pageable pageable);
    long countByOwnerAndCreatedAtAfter(String owner, Instant after);
}
