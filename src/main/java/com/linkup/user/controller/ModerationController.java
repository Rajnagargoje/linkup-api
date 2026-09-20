package com.linkup.user.controller;
import com.linkup.user.entity.ChatReport;
import com.linkup.user.repository.ChatReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/moderation/reports") @RequiredArgsConstructor
public class ModerationController {
    private final ChatReportRepository reports;
    @GetMapping public Page<ChatReport> list(@RequestParam(defaultValue = "0") int page) {
        return reports.findAll(PageRequest.of(Math.max(0, page), 25, Sort.by("createdAt").descending()));
    }
    @PatchMapping("/{id}/reviewed") public void reviewed(@PathVariable Long id) {
        var report = reports.findById(id).orElseThrow(() -> new IllegalArgumentException("Report not found."));
        report.setReviewed(true); reports.save(report);
    }
}
