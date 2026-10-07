package com.linkup.user.auth;

import com.linkup.user.dto.request.LoginDTO;
import com.linkup.user.dto.request.RegisterUserDTO;
import com.linkup.user.dto.response.ApiResponseDTO;
import com.linkup.user.service.UserService;
import com.linkup.user.service.impl.JWTService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** New endpoints leave the existing login/register contracts compatible with older APKs. */
@RestController @RequiredArgsConstructor
@RequestMapping(value = "/api/auth/session", consumes = MediaType.APPLICATION_JSON_VALUE)
public class AuthSessionController {
    private final UserService users;
    private final JWTService jwt;
    private final AuthSessionService sessions;

    public record RefreshRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String refreshToken,
            @NotBlank @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestId) {}
    public record LogoutRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String refreshToken) {}

    @PostMapping("/login")
    public ResponseEntity<ApiResponseDTO<AuthSessionResponse>> login(@Valid @RequestBody LoginDTO request) {
        String access = users.loginAuthenticatedUser(request);
        return reply(200, sessions.issue(jwt.extractUsername(access)));
    }
    @PostMapping("/register") @Transactional
    public ResponseEntity<ApiResponseDTO<AuthSessionResponse>> register(@Valid @RequestBody RegisterUserDTO request) {
        var created = users.register(request);
        return reply(201, sessions.issue(created.user().username()));
    }
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponseDTO<AuthSessionResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        return reply(200, sessions.refresh(request.refreshToken(), request.requestId()));
    }
    @PostMapping("/logout")
    public ResponseEntity<ApiResponseDTO<Void>> logout(@Valid @RequestBody LogoutRequest request) {
        sessions.revoke(request.refreshToken()).ifPresent(users::logout);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ApiResponseDTO<>(200, "Logged out", null));
    }
    private ResponseEntity<ApiResponseDTO<AuthSessionResponse>> reply(int status, AuthSessionResponse data) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache").body(new ApiResponseDTO<>(status, "OK", data));
    }
}
