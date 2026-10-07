package com.linkup.user.notification;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.*;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.util.Base64;

@Configuration @EnableScheduling
public class PushConfiguration {
    @Bean(destroyMethod = "delete") @ConditionalOnProperty(name = "linkup.push.enabled", havingValue = "true")
    public FirebaseApp firebaseApp(@Value("${linkup.push.project-id}") String projectId,
            @Value("${FIREBASE_CREDENTIALS_BASE64:}") String encodedCredentials) throws IOException {
        return FirebaseApp.initializeApp(FirebaseOptions.builder().setProjectId(projectId)
            .setCredentials(credentials(encodedCredentials)).setConnectTimeout(5000)
            .setReadTimeout(10000).build(), "linkup-push");
    }
    @Bean @ConditionalOnProperty(name = "linkup.push.enabled", havingValue = "true")
    public FirebaseMessaging firebaseMessaging(FirebaseApp app) { return FirebaseMessaging.getInstance(app); }

    static GoogleCredentials credentials(String encoded) throws IOException {
        // Local development retains GOOGLE_APPLICATION_CREDENTIALS / ADC support.
        if (encoded == null || encoded.isBlank()) return GoogleCredentials.getApplicationDefault();
        try (var stream = new ByteArrayInputStream(Base64.getDecoder().decode(encoded.strip()))) {
            // Accept service-account JSON only, never arbitrary credential configurations.
            return ServiceAccountCredentials.fromStream(stream);
        } catch (IOException | RuntimeException invalid) {
            // Credential parser exceptions may contain secret input. Do not propagate them.
            throw new IOException("FIREBASE_CREDENTIALS_BASE64 must contain Base64-encoded Firebase Admin service-account JSON.");
        }
    }
}
