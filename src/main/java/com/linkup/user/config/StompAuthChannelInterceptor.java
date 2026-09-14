package com.linkup.user.config;

import com.linkup.user.entity.User;
import com.linkup.user.repository.UserRepository;
import com.linkup.user.service.impl.JWTService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

/**
 * Runs on every inbound STOMP frame. The only one we care about is
 * CONNECT — that's where we authenticate the socket, exactly once, and
 * attach a Principal to the session. Every frame after that (SUBSCRIBE,
 * SEND) on this session inherits that Principal, which is what lets
 * ChatController trust `Principal.getName()` as the real sender instead
 * of a client-supplied field in the message body.
 *
 * The SockJS HTTP handshake itself stays permitAll in SecurityConfig
 * (it can't carry a Bearer header the way a normal request does) — this
 * interceptor is where the actual auth decision happens for chat.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);

    private final JWTService jwtService;
    private final UserRepository userRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                logger.warn("STOMP CONNECT rejected: missing Authorization header");
                throw new BadCredentialsException("Missing or invalid Authorization header");
            }

            String token = authHeader.substring(7);
            String username = jwtService.extractUsername(token);

            if (username == null) {
                logger.warn("STOMP CONNECT rejected: invalid/expired token");
                throw new BadCredentialsException("Invalid or expired token");
            }

            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                throw new BadCredentialsException("Unknown user");
            }

            User user = userOpt.get();

            if (Boolean.TRUE.equals(user.getIsBanned()) || Boolean.TRUE.equals(user.getIsDeleted())
                    || !Boolean.TRUE.equals(user.getIsActive())) {
                logger.warn("STOMP CONNECT rejected for disabled account: {}", username);
                throw new BadCredentialsException("Account is not active");
            }

            Integer tokenVersion = jwtService.extractTokenVersion(token);
            if (tokenVersion == null || !tokenVersion.equals(user.getTokenVersion())) {
                logger.warn("STOMP CONNECT rejected: stale token for {}", username);
                throw new BadCredentialsException("Session has been invalidated, please log in again");
            }

            Principal principal = new UsernamePasswordAuthenticationToken(
                    username, null, List.of(new SimpleGrantedAuthority(user.getRole().name()))
            );
            accessor.setUser(principal);
        }

        return message;
    }
}
