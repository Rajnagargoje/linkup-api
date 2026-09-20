package com.linkup.user;
import com.linkup.user.config.StompAuthChannelInterceptor;
import com.linkup.user.repository.UserRepository;
import com.linkup.user.service.GuestSessionService;
import com.linkup.user.service.impl.JWTService;
import org.junit.jupiter.api.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.BadCredentialsException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GuestSocketPermissionsTest {
    GuestSessionService guests = mock(GuestSessionService.class);
    StompAuthChannelInterceptor interceptor = new StompAuthChannelInterceptor(mock(JWTService.class), mock(UserRepository.class), guests);
    private void frame(StompCommand command, String destination) {
        var headers = StompHeaderAccessor.create(command);
        headers.setUser(new GuestSessionService.GuestPrincipal("guest")); headers.setDestination(destination);
        headers.setLeaveMutable(true);
        interceptor.preSend(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), null);
    }
    @Test void guestCanUseOnlyOwnRandomQueueAndRandomActions() {
        assertDoesNotThrow(() -> frame(StompCommand.SUBSCRIBE, "/user/queue/random"));
        assertDoesNotThrow(() -> frame(StompCommand.SEND, "/app/random/join"));
        assertThrows(BadCredentialsException.class, () -> frame(StompCommand.SEND, "/app/chat.send"));
        assertThrows(BadCredentialsException.class, () -> frame(StompCommand.SEND, "/app/sendMessage/room"));
        assertThrows(BadCredentialsException.class, () -> frame(StompCommand.SUBSCRIBE, "/user/queue/conversations/1"));
        assertThrows(BadCredentialsException.class, () -> frame(StompCommand.SUBSCRIBE, "/queue/random-user-other"));
        assertThrows(BadCredentialsException.class, () -> frame(StompCommand.SUBSCRIBE, "/topic/**"));
        assertThrows(BadCredentialsException.class, () -> frame(StompCommand.SEND, "/user/queue/random"));
    }
}
