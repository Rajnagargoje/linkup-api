package com.linkup.user.service.impl;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;

@Service
public class TransactionalEmailSender {
    private final ObjectProvider<JavaMailSender> smtp;
    private final RestClient http;
    private final String provider;
    private final String apiKey;

    public TransactionalEmailSender(ObjectProvider<JavaMailSender> smtp, RestClient brevoClient,
            @Value("${app.mail.provider:smtp}") String provider,
            @Value("${BREVO_API_KEY:}") String apiKey) {
        this.smtp = smtp;
        this.provider = provider;
        this.apiKey = apiKey;
        if (!List.of("smtp", "brevo").contains(provider)) throw new IllegalStateException("Unsupported email provider.");
        if (provider.equals("brevo") && apiKey.isBlank()) throw new IllegalStateException("BREVO_API_KEY is required.");
        this.http = brevoClient;
    }

    public void send(SimpleMailMessage message) {
        if (provider.equals("brevo")) {
            http.post().uri("/smtp/email").header("api-key", apiKey).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("sender", Map.of("name", "LinkUp", "email", message.getFrom()),
                    "to", List.of(Map.of("email", message.getTo()[0])),
                    "subject", message.getSubject(), "textContent", message.getText()))
                .retrieve().toBodilessEntity();
        } else {
            var sender = smtp.getIfAvailable();
            if (sender == null) throw new IllegalStateException("SMTP is not configured.");
            sender.send(message);
        }
    }
}
