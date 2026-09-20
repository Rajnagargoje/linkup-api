package com.linkup.user;

import com.linkup.user.entity.*;
import com.linkup.user.repository.*;
import com.linkup.user.service.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.BadCredentialsException;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GuestSessionServiceTest {
    GuestSessionRepository guests;
    UserRepository users;
    ChatRelationshipPolicy policy;
    GuestSessionService service;
    @BeforeEach void setup() {
        guests = mock(GuestSessionRepository.class); users = mock(UserRepository.class); policy = mock(ChatRelationshipPolicy.class);
        service = new GuestSessionService(guests, users, policy);
    }
    @Test void issuesOpaqueTokenAndStoresOnlyHash() {
        var credentials = service.create("  Visitor  ");
        var captor = org.mockito.ArgumentCaptor.forClass(GuestSession.class);
        verify(guests).save(captor.capture());
        assertEquals("Visitor", credentials.displayName());
        assertEquals(43, credentials.token().length());
        assertEquals(64, captor.getValue().getTokenHash().length());
        assertNotEquals(credentials.token(), captor.getValue().getTokenHash());
        assertTrue(credentials.expiresAt().isAfter(Instant.now()));
    }
    @Test void rejectsInvalidNamesAndExpiredGuestSessions() {
        assertThrows(IllegalArgumentException.class, () -> service.create(" "));
        assertThrows(IllegalArgumentException.class, () -> service.create("a\nb"));
        var guest = new GuestSession(); guest.setId("id"); guest.setExpiresAt(Instant.now().minusSeconds(1));
        when(guests.findByTokenHash(anyString())).thenReturn(Optional.of(guest));
        assertThrows(BadCredentialsException.class, () -> service.authenticate("x".repeat(43)));
    }
    @Test void claimMigratesBlocksAndCannotBeClaimedByAnotherAccount() {
        var guest = new GuestSession(); guest.setId("id"); guest.setExpiresAt(Instant.now().plusSeconds(600));
        var alice = new User(); alice.setPublicId("alice-id"); alice.setUsername("alice");
        when(guests.findForClaim(anyString())).thenReturn(Optional.of(guest));
        when(users.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(users.findByPublicId("alice-id")).thenReturn(Optional.of(alice));
        service.claim("x".repeat(43), "alice");
        assertEquals("alice-id", guest.getAccountPublicId());
        verify(policy).migrateGuest("g:id", "u:alice-id");
        var bob = new User(); bob.setPublicId("bob-id"); bob.setUsername("bob");
        when(users.findByUsername("bob")).thenReturn(Optional.of(bob));
        assertThrows(IllegalArgumentException.class, () -> service.claim("x".repeat(43), "bob"));
    }
    @Test void linkedSessionHonorsAccountRevocation() {
        var guest = new GuestSession(); guest.setExpiresAt(Instant.now().plusSeconds(600)); guest.setAccountPublicId("alice-id"); guest.setAccountTokenVersion(0);
        var user = new User(); user.setTokenVersion(1);
        when(guests.findById("id")).thenReturn(Optional.of(guest));
        when(users.findByPublicId("alice-id")).thenReturn(Optional.of(user));
        assertThrows(BadCredentialsException.class, () -> service.resolve(new GuestSessionService.GuestPrincipal("id")));
    }
}
