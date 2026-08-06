package com.taskmanager.task_manager_api.util;

import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for JWT claim extraction.
 * Uses a fake JWT with a known payload — no real Cognito needed.
 */
class JwtUtilTest {

    // Build a fake JWT with known claims for testing
    private static String buildFakeJwt(String sub, String jti, long exp, String email) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(String.format(
                        "{\"sub\":\"%s\",\"jti\":\"%s\",\"exp\":%d,\"email\":\"%s\"}",
                        sub, jti, exp, email).getBytes());
        return header + "." + payload + ".fakesignature";
    }

    private static final long FUTURE_EXP = System.currentTimeMillis() / 1000 + 3600;
    private static final String TEST_TOKEN = buildFakeJwt(
            "user-sub-123", "jti-abc-456", FUTURE_EXP, "test@example.com");

    @Test
    void extractUserId_shouldReturnSub() {
        assertThat(JwtUtil.extractUserId(TEST_TOKEN)).isEqualTo("user-sub-123");
    }

    @Test
    void extractJti_shouldReturnJti() {
        assertThat(JwtUtil.extractJti(TEST_TOKEN)).isEqualTo("jti-abc-456");
    }

    @Test
    void extractExp_shouldReturnExpiry() {
        assertThat(JwtUtil.extractExp(TEST_TOKEN)).isEqualTo(FUTURE_EXP);
    }

    @Test
    void extractEmail_shouldReturnEmail() {
        assertThat(JwtUtil.extractEmail(TEST_TOKEN)).isEqualTo("test@example.com");
    }

    @Test
    void extractUserId_shouldThrow_onInvalidFormat() {
        assertThatThrownBy(() -> JwtUtil.extractUserId("not.a.valid.jwt.format"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void extractUserId_shouldThrow_onMalformedToken() {
        assertThatThrownBy(() -> JwtUtil.extractUserId("invalid"))
                .isInstanceOf(RuntimeException.class);
    }
}