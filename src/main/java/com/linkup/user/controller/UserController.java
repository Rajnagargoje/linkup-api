package com.linkup.user.controller;


import com.linkup.user.dto.request.LoginDTO;
import com.linkup.user.dto.request.RegisterUserDTO;
import com.linkup.user.dto.request.UpdateProfileDTO;
import com.linkup.user.dto.response.ApiResponseDTO;
import com.linkup.user.dto.response.AuthResponseDTO;
import com.linkup.user.dto.response.UserDTO;
import com.linkup.user.dto.response.UsernameAvailabilityResponse;
import com.linkup.user.service.UserService;
import com.linkup.user.utils.SimpleRateLimiter;
import com.linkup.user.utils.Status;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final SimpleRateLimiter rateLimiter;

    // Public (see SecurityConfig) — runs during signup, before an
    // account/token exists. Rate-limited per IP since it's an
    // unavoidable small username-enumeration surface otherwise.
    @GetMapping("/check-username")
    public ResponseEntity<ApiResponseDTO<UsernameAvailabilityResponse>> checkUsername(
            @RequestParam String username,
            HttpServletRequest request
    ) {
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = request.getRemoteAddr();
        }

        if (!rateLimiter.tryConsume("check-username:" + clientIp, 20, 60_000)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ApiResponseDTO<>(429, "Too many requests, please slow down", null));
        }

        UsernameAvailabilityResponse result = userService.checkUsernameAvailability(username);
        return ResponseEntity.ok(new ApiResponseDTO<>(HttpStatus.OK.value(), "OK", result));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponseDTO<AuthResponseDTO>> register(@Valid @RequestBody RegisterUserDTO dto) {
        logger.info("Received request to register user: {} ", dto.username());
        AuthResponseDTO created = userService.register(dto);
        return new ResponseEntity<>(
                new ApiResponseDTO<>(HttpStatus.CREATED.value(), "User registered successfully", created),
                HttpStatus.CREATED
        );
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponseDTO<String>> login(@Valid @RequestBody LoginDTO authRequest) {
        logger.info("Received login request for: {}", authRequest.username());
        String token = userService.loginAuthenticatedUser(authRequest);
        return ResponseEntity.ok(new ApiResponseDTO<>(HttpStatus.OK.value(), "Login successful", token));
    }

    // Replaces the old "GET /takeid/{id}", which let any authenticated
    // user fetch ANY other user's full profile by walking sequential IDs.
    // This always resolves to whoever the JWT belongs to.
    @GetMapping("/me")
    public ResponseEntity<ApiResponseDTO<UserDTO>> me(Principal principal) {
        UserDTO me = userService.getMe(principal.getName());
        return ResponseEntity.ok(new ApiResponseDTO<>(HttpStatus.OK.value(), "OK", me));
    }

    // Also served at /details for backwards compatibility with the
    // frontend's current api.config.ts — point it at /me when convenient.
    @GetMapping("/details")
    public ResponseEntity<ApiResponseDTO<UserDTO>> details(Principal principal) {
        return me(principal);
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponseDTO<UserDTO>> updateProfile(
            Principal principal,
            @Valid @RequestBody UpdateProfileDTO dto
    ) {
        UserDTO updated = userService.updateProfile(principal.getName(), dto);
        return ResponseEntity.ok(new ApiResponseDTO<>(HttpStatus.OK.value(), "Profile updated", updated));
    }

    @PatchMapping("/status")
    public ResponseEntity<ApiResponseDTO<Void>> updateStatus(
            Principal principal,
            @RequestParam Status status
    ) {
        userService.updateStatus(principal.getName(), status);
        return ResponseEntity.ok(new ApiResponseDTO<>(HttpStatus.OK.value(), "Status updated", null));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponseDTO<Void>> logout(Principal principal) {
        userService.logout(principal.getName());
        return ResponseEntity.ok(new ApiResponseDTO<>(HttpStatus.OK.value(), "Logged out", null));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponseDTO<Void>> deleteAccount(Principal principal) {
        userService.deleteAccount(principal.getName());
        return ResponseEntity.ok(new ApiResponseDTO<>(HttpStatus.OK.value(), "Account deleted", null));
    }
}
