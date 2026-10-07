package com.linkup.user.auth;

import com.linkup.user.dto.response.AuthResponseDTO;
import com.linkup.user.entity.User;
import com.linkup.user.exception.GlobalExceptionHandler;
import com.linkup.user.filter.JwtAuthFilter;
import com.linkup.user.mapper.UserMapper;
import com.linkup.user.service.UserService;
import com.linkup.user.service.impl.JWTService;
import com.linkup.user.service.impl.UserPrinciples;
import com.linkup.user.utils.Role;
import com.linkup.user.utils.Status;
import org.junit.jupiter.api.*;
import org.mapstruct.factory.Mappers;
import org.springframework.http.MediaType;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthSessionHttpTest {
    final UserService users = mock(UserService.class);
    final AuthSessionService sessions = mock(AuthSessionService.class);
    final JWTService jwt = new JWTService();
    User user;
    MockMvc mvc;
    AuthSessionResponse response;
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(jwt, "secret", "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTA=");
        ReflectionTestUtils.setField(jwt, "expirationMs", 1800000L);
        user = new User(); user.setPublicId("alice-id"); user.setUsername("alice"); user.setPassword("unused");
        user.setRole(Role.USER); user.setStatus(Status.OFFLINE); user.setIsBanned(false);
        var profile = Mappers.getMapper(UserMapper.class).toUserDTO(user);
        response = new AuthSessionResponse(jwt.generateToken("alice", 0, "device-one"), "a".repeat(43), Instant.now().plusSeconds(86400), profile);
        when(sessions.issue("alice")).thenReturn(response);
        when(users.loginAuthenticatedUser(any())).thenReturn(jwt.generateToken("alice", 0));
        when(users.register(any())).thenReturn(new AuthResponseDTO(profile, jwt.generateToken("alice", 0)));
        mvc = MockMvcBuilders.standaloneSetup(new AuthSessionController(users, jwt, sessions))
                .defaultRequest(post("/").accept(MediaType.APPLICATION_JSON))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        SecurityContextHolder.clearContext();
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void loginReturnsRenewableSessionAndDisablesCaching() throws Exception {
        mvc.perform(post("/api/auth/session/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"Secret123!\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.refreshToken").value("a".repeat(43)))
                .andExpect(jsonPath("$.data.user.username").value("alice"));
    }
    @Test void registrationKeepsTheUserAndTokenShape() throws Exception {
        mvc.perform(post("/api/auth/session/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"email\":\"alice@example.test\",\"password\":\"Secret123!\"}"))
                .andExpect(status().isCreated()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.token").isString())
                .andExpect(jsonPath("$.data.user.publicId").value("alice-id"));
    }
    @Test void malformedRefreshRequestIsRejectedBeforeServiceRuns() throws Exception {
        mvc.perform(post("/api/auth/session/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"bad\",\"requestId\":\"bad\"}"))
                .andExpect(status().isBadRequest());
        verify(sessions, never()).refresh(any(), any());
    }
    @Test void revokedDeviceCannotAuthenticateWithItsUnexpiredAccessToken() throws Exception {
        var details = mock(UserDetailsService.class); when(details.loadUserByUsername("alice")).thenReturn(new UserPrinciples(user));
        var filter = new JwtAuthFilter(jwt, details, sessions);
        var request = new MockHttpServletRequest("GET", "/api/user/me");
        request.addHeader("Authorization", "Bearer " + response.token());
        when(sessions.isActive("device-one", "alice")).thenReturn(false);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
    @Test void activeDeviceCanAuthenticateWithItsAccessToken() throws Exception {
        var details = mock(UserDetailsService.class); when(details.loadUserByUsername("alice")).thenReturn(new UserPrinciples(user));
        var request = new MockHttpServletRequest("GET", "/api/user/me");
        request.addHeader("Authorization", "Bearer " + response.token());
        when(sessions.isActive("device-one", "alice")).thenReturn(true);
        new JwtAuthFilter(jwt, details, sessions).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertEquals("alice", SecurityContextHolder.getContext().getAuthentication().getName());
    }
    @Test void socketKeepsSessionIdentityAndRejectsFramesAfterRevocation() {
        var repository = mock(com.linkup.user.repository.UserRepository.class);
        when(repository.findByUsername("alice")).thenReturn(java.util.Optional.of(user));
        when(sessions.isActive("device-one", "alice")).thenReturn(true);
        var interceptor = new com.linkup.user.config.StompAuthChannelInterceptor(jwt, repository,
                mock(com.linkup.user.service.GuestSessionService.class), sessions);
        var connect = org.springframework.messaging.simp.stomp.StompHeaderAccessor.create(org.springframework.messaging.simp.stomp.StompCommand.CONNECT);
        connect.setNativeHeader("Authorization", "Bearer " + response.token()); connect.setLeaveMutable(true);
        interceptor.preSend(org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0], connect.getMessageHeaders()), null);
        assertEquals("device-one", connect.getSessionAttributes().get("linkup.authSession"));
        var subscribe = org.springframework.messaging.simp.stomp.StompHeaderAccessor.create(org.springframework.messaging.simp.stomp.StompCommand.SUBSCRIBE);
        subscribe.setUser(connect.getUser()); subscribe.setSessionAttributes(connect.getSessionAttributes());
        subscribe.setDestination("/user/queue/notifications"); subscribe.setLeaveMutable(true);
        when(sessions.isActive("device-one", "alice")).thenReturn(false);
        assertThrows(org.springframework.security.authentication.BadCredentialsException.class,
                () -> interceptor.preSend(org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0], subscribe.getMessageHeaders()), null));
    }
}
