package com.linkup.user.notification;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.*;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.io.IOException;

@Configuration @EnableScheduling
public class PushConfiguration {
    @Bean(destroyMethod = "delete") @ConditionalOnProperty(name = "linkup.push.enabled", havingValue = "true")
    public FirebaseApp firebaseApp(@Value("${linkup.push.project-id}") String projectId) throws IOException {
        return FirebaseApp.initializeApp(FirebaseOptions.builder().setProjectId(projectId)
            .setCredentials(GoogleCredentials.getApplicationDefault()).setConnectTimeout(5000)
            .setReadTimeout(10000).build(), "linkup-push");
    }
    @Bean @ConditionalOnProperty(name = "linkup.push.enabled", havingValue = "true")
    public FirebaseMessaging firebaseMessaging(FirebaseApp app) { return FirebaseMessaging.getInstance(app); }
}
