package com.linkup.user.service.impl;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class TransactionalEmailSender {

    private final ObjectProvider<JavaMailSender> smtp;
    private final RestClient http;
    private final String provider;
    private final String apiKey;

    public TransactionalEmailSender(
            ObjectProvider<JavaMailSender> smtp,
            RestClient resendClient,
            @Value("${app.mail.provider:smtp}") String provider,
            @Value("${RESEND_API_KEY:}") String apiKey
    ) {
        this.smtp = smtp;
        this.provider = provider;
        this.apiKey = apiKey;

        if (!List.of("smtp", "resend").contains(provider)) {
            throw new IllegalStateException("Unsupported email provider.");
        }

        if (provider.equals("resend") && apiKey.isBlank()) {
            throw new IllegalStateException("RESEND_API_KEY is required.");
        }

        this.http = resendClient;
    }

    public void send(SimpleMailMessage message) {

        if (provider.equals("resend")) {

            String[] recipients = message.getTo();

            if (recipients == null || recipients.length == 0) {
                throw new IllegalArgumentException("Email recipient is required.");
            }

            if (message.getFrom() == null || message.getFrom().isBlank()) {
                throw new IllegalArgumentException("Email sender is required.");
            }

            http.post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "from", "LinkUp <" + message.getFrom() + ">",
                            "to", List.of(recipients),
                            "subject",
                            message.getSubject() == null
                                    ? "LinkUp"
                                    : message.getSubject(),
                            "text",
                            message.getText() == null
                                    ? ""
                                    : message.getText()
                    ))
                    .retrieve()
                    .toBodilessEntity();

            return;
        }

        var sender = smtp.getIfAvailable();

        if (sender == null) {
            throw new IllegalStateException("SMTP is not configured.");
        }

        sender.send(message);
    }
}