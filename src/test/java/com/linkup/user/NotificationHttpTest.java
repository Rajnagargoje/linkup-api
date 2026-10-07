package com.linkup.user;

import com.linkup.user.entity.User;
import com.linkup.user.exception.GlobalExceptionHandler;
import com.linkup.user.notification.*;
import com.linkup.user.service.impl.JWTService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NotificationHttpTest {
    NotificationService service=mock(NotificationService.class);
    JWTService jwt=mock(JWTService.class);
    MockMvc mvc;
    @SuppressWarnings("unchecked") @BeforeEach void setup() {
        mvc=MockMvcBuilders.standaloneSetup(new NotificationController(service,jwt,mock(ObjectProvider.class)))
            .setControllerAdvice(new GlobalExceptionHandler())
            .defaultRequest(post("/").accept(MediaType.APPLICATION_JSON).principal(() -> "alice")).build();
    }
    @Test void registrationUsesAuthenticatedOwnerAndSignedTokenSession() throws Exception {
        when(jwt.extractSessionId("signed-token")).thenReturn("trusted-session");
        mvc.perform(post("/api/notifications/devices").header("Authorization","Bearer signed-token").contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"a-valid-fcm-token-long-enough\",\"installationId\":\"12345678-1234-1234-1234-123456789012\",\"userId\":\"someone-else\",\"authSessionId\":\"fake-session\"}"))
            .andExpect(status().isOk());
        verify(service).register("alice","a-valid-fcm-token-long-enough","12345678-1234-1234-1234-123456789012","trusted-session");
    }
    @Test void malformedRegistrationIsRejectedBeforeWriting() throws Exception {
        mvc.perform(post("/api/notifications/devices").header("Authorization","Bearer signed-token").contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"bad\",\"installationId\":\"bad\"}")).andExpect(status().isBadRequest());
        verify(service,never()).register(any(),any(),any(),any());
    }
    @Test void olderClientPreferenceUpdatesPreserveNewCategoryOptOuts() throws Exception {
        User owner=new User(); owner.setPublicId("alice-id"); when(service.user("alice")).thenReturn(owner);
        var saved=new NotificationPreferences(); saved.setSupportReplies(false);
        when(service.preferencesFor("alice-id")).thenReturn(saved); when(service.savePreferences("alice",saved)).thenReturn(saved);
        mvc.perform(put("/api/notifications/preferences").contentType(MediaType.APPLICATION_JSON).content("{\"messages\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.supportReplies").value(false)).andExpect(jsonPath("$.messages").value(false));
    }
}
