package com.linkup.user.service.impl;

import com.linkup.user.entity.EmailVerificationCode;
import com.linkup.user.entity.User;
import com.linkup.user.exception.ResourceNotFoundException;
import com.linkup.user.repository.EmailVerificationCodeRepository;
import com.linkup.user.repository.UserRepository;
import com.linkup.user.utils.VerificationPurpose;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final Logger logger = LoggerFactory.getLogger(EmailVerificationService.class);

    private static final int CODE_LENGTH = 6;
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailVerificationCodeRepository codeRepository;
    private final UserRepository userRepository;
    // Reusing the app's existing BCryptPasswordEncoder bean to hash OTPs —
    // same principle as password hashing (never store the raw code),
    // without pulling in a second hashing library for a 6-digit number.
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    @Value("${app.mail.from:no-reply@linkup.app}")
    private String fromAddress;

    @Value("${app.otp.email.cooldown-seconds:60}")
    private long cooldownSeconds;

    @Value("${app.otp.email.expiry-minutes:30}")
    private long expiryMinutes;

    @Transactional
    public void sendVerificationCode(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new IllegalArgumentException("Email is already verified");
        }

        Optional<EmailVerificationCode> latest = codeRepository
                .findTopByUsernameAndPurposeAndConsumedFalseOrderByCreatedAtDesc(
                        username, VerificationPurpose.EMAIL_VERIFICATION);

        if (latest.isPresent()) {
            LocalDateTime canResendAt = latest.get().getCreatedAt().plusSeconds(cooldownSeconds);
            if (LocalDateTime.now().isBefore(canResendAt)) {
                long secondsLeft = ChronoUnit.SECONDS.between(LocalDateTime.now(), canResendAt);
                throw new IllegalArgumentException(
                        "Please wait " + secondsLeft + "s before requesting another code");
            }
            // Superseded by the new one below — only one live code per user/purpose at a time.
            latest.get().setConsumed(true);
            codeRepository.save(latest.get());
        }

        String code = generateCode();

        EmailVerificationCode record = new EmailVerificationCode();
        record.setUsername(username);
        record.setEmail(user.getEmail());
        record.setCodeHash(passwordEncoder.encode(code));
        record.setPurpose(VerificationPurpose.EMAIL_VERIFICATION);
        record.setExpiresAt(LocalDateTime.now().plusMinutes(expiryMinutes));
        record.setAttempts(0);
        record.setConsumed(false);
        codeRepository.save(record);

        sendEmail(user.getEmail(), code);
    }

    @Transactional
    public void verifyCode(String username, String code) {
        EmailVerificationCode record = codeRepository
                .findTopByUsernameAndPurposeAndConsumedFalseOrderByCreatedAtDesc(
                        username, VerificationPurpose.EMAIL_VERIFICATION)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No verification code found. Please request a new one."));

        if (record.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("This code has expired. Please request a new one.");
        }

        if (record.getAttempts() >= MAX_ATTEMPTS) {
            throw new IllegalArgumentException(
                    "Too many incorrect attempts. Please request a new code.");
        }

        if (!passwordEncoder.matches(code, record.getCodeHash())) {
            record.setAttempts(record.getAttempts() + 1);
            codeRepository.save(record);
            throw new IllegalArgumentException("Incorrect code");
        }

        record.setConsumed(true);
        codeRepository.save(record);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        user.setEmailVerified(true);
        userRepository.save(user);
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private void sendEmail(String toAddress, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toAddress);
        message.setSubject("Your LinkUp verification code");
        message.setText(
                "Your verification code is: " + code + "\n\n" +
                "This code expires in " + expiryMinutes + " minutes. " +
                "If you didn't request this, you can ignore this email."
        );

        try {
            mailSender.send(message);
        } catch (Exception ex) {
            // Don't leak SMTP/provider details to the client — log it
            // server-side and surface a generic failure instead.
            logger.error("Failed to send verification email to {}", toAddress, ex);
            throw new IllegalArgumentException("Could not send verification email. Please try again shortly.");
        }
    }
}
