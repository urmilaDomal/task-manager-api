package com.taskmanager.task_manager_api.service;

import com.taskmanager.task_manager_api.model.TokenBlocklist;
import com.taskmanager.task_manager_api.repository.InMemoryTokenBlocklistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
class TokenBlocklistServiceTest {

    @Mock
    private InMemoryTokenBlocklistRepository inMemoryRepository;

    @InjectMocks
    private TokenBlocklistService tokenBlocklistService;

    private static final String TEST_USER_ID = "user-sub-123";
    private static final String TEST_JTI = "jti-abc-456";
    private static final long FUTURE_EXP = System.currentTimeMillis() / 1000 + 3600;

    private String fakeToken;

    @BeforeEach
    void setUp() {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(String.format(
                        "{\"sub\":\"%s\",\"jti\":\"%s\",\"exp\":%d}",
                        TEST_USER_ID, TEST_JTI, FUTURE_EXP).getBytes());
        fakeToken = header + "." + payload + ".fakesig";
    }

    // ── revokeToken ───────────────────────────────────────────

    @Test
    void revokeToken_shouldSaveToBlocklist() {
        tokenBlocklistService.revokeToken(fakeToken, TEST_USER_ID);

        ArgumentCaptor<TokenBlocklist> captor =
                ArgumentCaptor.forClass(TokenBlocklist.class);
        verify(inMemoryRepository, times(1)).save(captor.capture());

        TokenBlocklist saved = captor.getValue();
        assertThat(saved.getTokenId()).isEqualTo(TEST_JTI);
        assertThat(saved.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(saved.getTtl()).isEqualTo(FUTURE_EXP);
        assertThat(saved.getRevokedAt()).isNotNull();
    }

    @Test
    void revokeToken_shouldSetTtlToTokenExpiry() {
        tokenBlocklistService.revokeToken(fakeToken, TEST_USER_ID);

        ArgumentCaptor<TokenBlocklist> captor =
                ArgumentCaptor.forClass(TokenBlocklist.class);
        verify(inMemoryRepository).save(captor.capture());

        // TTL should match token expiry so DynamoDB auto-cleans it
        assertThat(captor.getValue().getTtl()).isEqualTo(FUTURE_EXP);
    }

    // ── isRevoked ─────────────────────────────────────────────

    @Test
    void isRevoked_shouldReturnTrue_whenTokenBlocklisted() {
        when(inMemoryRepository.isBlocklisted(TEST_JTI)).thenReturn(true);

        assertThat(tokenBlocklistService.isRevoked(fakeToken)).isTrue();
        verify(inMemoryRepository).isBlocklisted(TEST_JTI);
    }

    @Test
    void isRevoked_shouldReturnFalse_whenTokenNotBlocklisted() {
        when(inMemoryRepository.isBlocklisted(TEST_JTI)).thenReturn(false);

        assertThat(tokenBlocklistService.isRevoked(fakeToken)).isFalse();
    }

    @Test
    void isRevoked_shouldUseJti_notFullToken() {
        // Verify we check by jti (short UUID) not the full 800-char token
        when(inMemoryRepository.isBlocklisted(TEST_JTI)).thenReturn(false);

        tokenBlocklistService.isRevoked(fakeToken);

        // Should be called with just the jti, not the full token string
        verify(inMemoryRepository).isBlocklisted(TEST_JTI);
        verify(inMemoryRepository, never()).isBlocklisted(fakeToken);
    }
}