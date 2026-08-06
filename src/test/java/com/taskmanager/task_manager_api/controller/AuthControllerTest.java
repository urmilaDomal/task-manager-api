package com.taskmanager.task_manager_api.controller;

import com.taskmanager.task_manager_api.exception.GlobalExceptionHandler;
import com.taskmanager.task_manager_api.service.TokenBlocklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TokenBlocklistService tokenBlocklistService;

    private static final String TEST_USER_ID = "user-sub-123";
    private static final String FAKE_JWT = buildFakeJwt(TEST_USER_ID);

    private static String buildFakeJwt(String sub) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(String.format(
                        "{\"sub\":\"%s\",\"jti\":\"jti-123\",\"exp\":9999999999}",
                        sub).getBytes());
        return header + "." + payload + ".fakesig";
    }

    // ── POST /api/v1/auth/logout ──────────────────────────────

    @Test
    void logout_shouldReturn200_andRevokeToken() throws Exception {
        doNothing().when(tokenBlocklistService).revokeToken(anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", FAKE_JWT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"))
                .andExpect(jsonPath("$.userId").value(TEST_USER_ID));

        // Verify token was actually revoked
        verify(tokenBlocklistService, times(1)).revokeToken(FAKE_JWT, TEST_USER_ID);
    }

    @Test
    void logout_shouldReturn400_whenNoAuthHeader() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isBadRequest());

        verify(tokenBlocklistService, never()).revokeToken(any(), any());
    }

    @Test
    void logout_shouldCallRevokeWithCorrectUserId() throws Exception {
        doNothing().when(tokenBlocklistService).revokeToken(anyString(), eq(TEST_USER_ID));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", FAKE_JWT))
                .andExpect(status().isOk());

        // userId extracted from JWT sub claim — not from request body
        verify(tokenBlocklistService).revokeToken(FAKE_JWT, TEST_USER_ID);
    }
}