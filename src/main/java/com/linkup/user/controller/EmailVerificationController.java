package com.linkup.user.controller;

import com.linkup.user.dto.request.VerifyCodeRequest;
import com.linkup.user.dto.response.ApiResponseDTO;
import com.linkup.user.service.impl.EmailVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/auth/email")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    // No request body — always sends to whoever the token belongs to,
    // never an email address supplied by the client.
    @PostMapping("/send-code")
    public ResponseEntity<ApiResponseDTO<Void>> sendCode(Principal principal) {
        emailVerificationService.sendVerificationCode(principal.getName());
        return ResponseEntity.ok(new ApiResponseDTO<>(HttpStatus.OK.value(), "Verification code sent", null));
    }

    @PostMapping("/verify-code")
    public ResponseEntity<ApiResponseDTO<Void>> verifyCode(
            Principal principal,
            @Valid @RequestBody VerifyCodeRequest request
    ) {
        emailVerificationService.verifyCode(principal.getName(), request.code());
        return ResponseEntity.ok(new ApiResponseDTO<>(HttpStatus.OK.value(), "Email verified", null));
    }
}
