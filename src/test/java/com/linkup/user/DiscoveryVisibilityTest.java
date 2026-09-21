package com.linkup.user;

import com.linkup.user.controller.DiscoveryController;
import com.linkup.user.entity.User;
import com.linkup.user.repository.*;
import com.linkup.user.service.ChatRelationshipPolicy;
import com.linkup.user.service.impl.LocationServiceImpl;
import com.linkup.user.utils.ConnectionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DiscoveryVisibilityTest {
    private final ChatBlockRepository blocks = mock(ChatBlockRepository.class);
    private final ChatReportRepository reports = mock(ChatReportRepository.class);
    private final ConnectionRepository connections = mock(ConnectionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ChatRelationshipPolicy policy = new ChatRelationshipPolicy(blocks, connections, reports);

    private User user(long id, String name) {
        User user = new User();
        user.setId(id); user.setPublicId(name); user.setUsername(name);
        user.setIsActive(true); user.setLatitude(12.0); user.setLongitude(77.0);
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }

    @Test void hidesReportsEvenWithoutAnActiveBlock() {
        when(reports.existsByReporterAndTarget("u:a", "u:b")).thenReturn(true);
        assertTrue(policy.hiddenFromDiscovery("u:a", "u:b"));
        assertFalse(policy.hiddenFromDiscovery("u:b", "u:a"));
        assertFalse(policy.hiddenFromDiscovery("u:c", "u:b"));
    }

    @Test void hidesBlocksAndMigratesReportsWhenGuestRegisters() {
        when(blocks.blocks("u:a", "u:b")).thenReturn(true);
        assertTrue(policy.hiddenFromDiscovery("u:a", "u:b"));
        policy.migrateGuest("g:old", "u:new");
        verify(reports).migrateReporter("g:old", "u:new");
        verify(reports).migrateTarget("g:old", "u:new");
    }

    @Test void peopleHidesSelfBlockedAndReportedButKeepsOtherPeople() {
        User me = user(1, "me"), blocked = user(2, "blocked"), reported = user(3, "reported"), friend = user(4, "friend");
        when(users.findByUsername("me")).thenReturn(Optional.of(me));
        when(users.findByIsActiveTrueAndIsBannedFalseAndIsDeletedFalse(any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(me, blocked, reported, friend)));
        when(blocks.blocks("u:me", "u:blocked")).thenReturn(true);
        when(reports.existsByReporterAndTarget("u:me", "u:reported")).thenReturn(true);
        var result = new DiscoveryController(users, policy).discover(() -> "me", 0);
        assertEquals(List.of("friend"), result.stream().map(person -> person.getPublicId()).toList());
    }

    @Test void nearbyAlsoExcludesFriendsAndInactiveUsers() {
        User me = user(1, "me"), blocked = user(2, "blocked"), reported = user(3, "reported"),
            friend = user(4, "friend"), stranger = user(5, "stranger"), inactive = user(6, "inactive");
        inactive.setIsActive(false);
        when(users.findByUsername("me")).thenReturn(Optional.of(me));
        when(connections.findConnectedUserIds(1L, ConnectionStatus.ACCEPTED)).thenReturn(List.of(4L));
        when(users.findByLocationVisibleTrueAndLatitudeIsNotNullAndLongitudeIsNotNullAndIsDeletedFalseAndIsBannedFalse())
            .thenReturn(List.of(me, blocked, reported, friend, stranger, inactive));
        when(blocks.blocks("u:me", "u:blocked")).thenReturn(true);
        when(reports.existsByReporterAndTarget("u:me", "u:reported")).thenReturn(true);
        var result = new LocationServiceImpl(users, connections, policy).getNearbyPeople("me", 10);
        assertEquals(List.of("stranger"), result.stream().map(person -> person.getPublicId()).toList());
    }
}
