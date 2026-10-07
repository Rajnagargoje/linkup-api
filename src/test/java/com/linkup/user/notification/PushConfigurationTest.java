package com.linkup.user.notification;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class PushConfigurationTest {
    @Test void readsServiceAccountWithoutCallingFirebase() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String pem = "-----BEGIN PRIVATE KEY-----\\n"
            + Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
            + "\\n-----END PRIVATE KEY-----\\n";
        String json = "{\"type\":\"service_account\",\"project_id\":\"test-project\","
            + "\"private_key_id\":\"test-key\",\"private_key\":\"" + pem + "\","
            + "\"client_email\":\"test@test-project.iam.gserviceaccount.com\","
            + "\"client_id\":\"1234567890\",\"token_uri\":\"https://oauth2.googleapis.com/token\"}";
        var credentials = (ServiceAccountCredentials) PushConfiguration.credentials("  " + encode(json) + "  ");
        assertEquals("test-project", credentials.getProjectId());
        assertEquals("test@test-project.iam.gserviceaccount.com", credentials.getClientEmail());
    }

    @Test void rejectsInvalidInputWithoutLeakingIt() {
        for (String value : new String[] {"SECRET_NOT_BASE64!", encode("SECRET_INVALID_JSON"),
                encode("{\"type\":\"authorized_user\",\"refresh_token\":\"SECRET_TOKEN\"}")}) {
            var error = assertThrows(IOException.class, () -> PushConfiguration.credentials(value));
            assertFalse(error.getMessage().contains("SECRET"));
            assertNull(error.getCause());
        }
    }

    @Test void disabledPushDoesNotRequireCredentials() {
        new ApplicationContextRunner().withUserConfiguration(PushConfiguration.class)
            .withPropertyValues("linkup.push.enabled=false", "FIREBASE_CREDENTIALS_BASE64=INVALID!")
            .run(context -> {
                assertNull(context.getStartupFailure());
                assertTrue(context.getBeansOfType(FirebaseApp.class).isEmpty());
            });
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
