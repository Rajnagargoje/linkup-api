package com.linkup.user;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.linkup.user.service.impl.CloudinaryPhotoStorage;
import com.linkup.user.service.impl.PhotoStorageService;
import com.linkup.user.service.impl.TransactionalEmailSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CloudDeploymentTest {
    @Test void uploadsOnlyToCloudWhenConfigured() throws Exception {
        ObjectProvider<CloudinaryPhotoStorage> provider = mock(ObjectProvider.class);
        var remote = mock(CloudinaryPhotoStorage.class);
        when(provider.getIfAvailable()).thenReturn(remote);
        var storage = new PhotoStorageService(provider);
        ReflectionTestUtils.setField(storage, "maxSizeMb", 5);
        var image = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{1});
        when(remote.store(image)).thenReturn("https://res.cloudinary.com/demo/image/upload/test.jpg");
        assertTrue(storage.store(image).startsWith("https://"));
        verify(remote).store(image);
        assertThrows(IllegalArgumentException.class, () -> storage.store(
            new MockMultipartFile("file", "test.svg", "image/svg+xml", new byte[]{1})));
        verifyNoMoreInteractions(remote);
    }

    @Test void cloudDeleteRejectsOtherCloudsAndUnownedPaths() throws Exception {
        var cloud = spy(new Cloudinary(Map.of("cloud_name", "demo", "api_key", "test", "api_secret", "test")));
        var uploader = mock(Uploader.class);
        doReturn(uploader).when(cloud).uploader();
        var storage = new CloudinaryPhotoStorage(cloud);
        storage.delete("https://res.cloudinary.com/other/image/upload/v1/linkup/photos/12345678-1234-1234-1234-123456789abc.jpg");
        storage.delete("https://res.cloudinary.com/demo/image/upload/v1/another-folder/test.jpg");
        verifyNoInteractions(uploader);
        storage.delete("https://res.cloudinary.com/demo/image/upload/v1/linkup/photos/12345678-1234-1234-1234-123456789abc.jpg");
        verify(uploader).destroy(eq("linkup/photos/12345678-1234-1234-1234-123456789abc"), argThat(options -> Boolean.TRUE.equals(options.get("invalidate"))));
    }

    @Test void localDeletionCannotTraverseDirectories(@TempDir Path dir) throws Exception {
        ObjectProvider<CloudinaryPhotoStorage> provider = mock(ObjectProvider.class);
        var storage = new PhotoStorageService(provider);
        ReflectionTestUtils.setField(storage, "uploadDir", dir.resolve("photos").toString());
        ReflectionTestUtils.setField(storage, "baseUrl", "http://localhost/photos");
        var protectedFile = Files.writeString(dir.resolve("keep.txt"), "keep");
        storage.delete("http://localhost/photos/../keep.txt");
        assertTrue(Files.exists(protectedFile));
    }

    @Test void brevoUsesHttpsApiAndDoesNotUseSmtp() {
        ObjectProvider<JavaMailSender> smtp = mock(ObjectProvider.class);
        var builder = RestClient.builder().baseUrl("https://api.brevo.com/v3");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
            .andExpect(header("api-key", "test-key"))
            .andExpect(jsonPath("$.sender.email").value("sender@example.com"))
            .andExpect(jsonPath("$.to[0].email").value("recipient@example.com"))
            .andExpect(jsonPath("$.textContent").value("Verification code: 123456"))
            .andRespond(withSuccess());
        var sender = new TransactionalEmailSender(smtp, builder.build(), "brevo", "test-key");
        sender.send(message());
        server.verify();
        verifyNoInteractions(smtp);
    }

    @Test void providerFailureDoesNotSilentlyFallBackToSmtp() {
        ObjectProvider<JavaMailSender> smtp = mock(ObjectProvider.class);
        var builder = RestClient.builder().baseUrl("https://api.brevo.com/v3");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email")).andRespond(withServerError());
        var sender = new TransactionalEmailSender(smtp, builder.build(), "brevo", "test-key");
        assertThrows(RuntimeException.class, () -> sender.send(message()));
        verifyNoInteractions(smtp);
    }

    @Test void missingBrevoKeyFailsEarlyAndSmtpStillWorksLocally() {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        assertThrows(IllegalStateException.class, () -> new TransactionalEmailSender(provider, RestClient.create(), "brevo", ""));
        var smtp = mock(JavaMailSender.class);
        when(provider.getIfAvailable()).thenReturn(smtp);
        var sender = new TransactionalEmailSender(provider, RestClient.create(), "smtp", "");
        var message = message();
        sender.send(message);
        verify(smtp).send(message);
    }

    private SimpleMailMessage message() {
        var message = new SimpleMailMessage();
        message.setFrom("sender@example.com"); message.setTo("recipient@example.com");
        message.setSubject("LinkUp"); message.setText("Verification code: 123456");
        return message;
    }
}
