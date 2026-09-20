package com.linkup.user.controller;

import com.linkup.user.dto.response.NearbyPersonResponse;
import com.linkup.user.repository.UserRepository;
import com.linkup.user.service.ChatRelationshipPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.security.Principal;
import java.util.List;

@RestController @RequestMapping("/api/people") @RequiredArgsConstructor
public class DiscoveryController {
    private final UserRepository users;
    private final ChatRelationshipPolicy policy;
    @GetMapping("/discover") @Transactional(readOnly = true)
    public List<NearbyPersonResponse> discover(Principal principal, @RequestParam(defaultValue = "0") int page) {
        var current = users.findByUsername(principal.getName()).orElseThrow();
        return users.findByIsActiveTrueAndIsBannedFalseAndIsDeletedFalse(PageRequest.of(Math.max(0, page), 50, Sort.by("createdAt").descending()))
                .stream().filter(user -> !user.getId().equals(current.getId()))
                .filter(user -> !policy.blocked("u:" + current.getPublicId(), "u:" + user.getPublicId()))
                .map(user -> new NearbyPersonResponse(user.getPublicId(), user.getUsername(), user.getAge(), user.getProfilePhoto(),
                        null, user.getOnline(), user.getEmailVerified(), user.getCreatedAt().isAfter(java.time.LocalDateTime.now().minusDays(7)) ? "New here" : null, null)).toList();
    }
}
