package com.linkup.user.controller;
import com.linkup.user.entity.ChatReport;
import com.linkup.user.repository.ChatReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/moderation/reports") @RequiredArgsConstructor
public class ModerationController {
    private final ChatReportRepository reports;
    private final com.linkup.user.notification.NotificationService notifications;
    private final com.linkup.user.repository.UserRepository users;
    @GetMapping public Page<ChatReport> list(@RequestParam(defaultValue = "0") int page) {
        return reports.findAll(PageRequest.of(Math.max(0, page), 25, Sort.by("createdAt").descending()));
    }
    @org.springframework.transaction.annotation.Transactional
    @PatchMapping("/{id}/reviewed") public void reviewed(@PathVariable Long id) {
        var report = reports.findById(id).orElseThrow(() -> new IllegalArgumentException("Report not found."));
        if (report.isReviewed()) return;
        report.setReviewed(true); reports.save(report);
        if (report.getReporter().startsWith("u:"))
            users.findByPublicId(report.getReporter().substring(2)).ifPresent(user ->
                notifications.create(user, null, com.linkup.user.notification.NotificationType.REPORT_UPDATE,
                    report.getId(), null, "Your report was reviewed", "Thank you for helping keep LinkUp safe. Your report has been reviewed.", false));
    }
}
