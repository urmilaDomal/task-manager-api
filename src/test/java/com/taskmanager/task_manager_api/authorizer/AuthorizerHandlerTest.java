package com.taskmanager.task_manager_api.authorizer;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayCustomAuthorizerEvent;
import com.amazonaws.services.lambda.runtime.events.IamPolicyResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AuthorizerHandlerTest {

    private AuthorizerHandler handler;

    @Mock
    private Context context;

    private static final String METHOD_ARN =
            "arn:aws:execute-api:us-east-2:123456789:abc123/dev/GET/api/v1/tasks";

    @BeforeEach
    void setUp() {
        handler = new AuthorizerHandler();
    }

    // ── Helper methods ────────────────────────────────────────

    private static String buildToken(String sub, String jti, long exp) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(String.format(
                        "{\"sub\":\"%s\",\"jti\":\"%s\",\"exp\":%d}",
                        sub, jti, exp).getBytes());
        return header + "." + payload + ".fakesig";
    }

    private APIGatewayCustomAuthorizerEvent buildEvent(String token) {
        APIGatewayCustomAuthorizerEvent event =
                new APIGatewayCustomAuthorizerEvent();
        event.setAuthorizationToken(token);
        event.setMethodArn(METHOD_ARN);
        return event;
    }

    /**
     * Extracts the effect ("Allow" or "Deny") from the IamPolicyResponse.
     * IamPolicyResponse.getPolicyDocument() returns Map<String,Object>
     * not a typed PolicyDocument — must cast manually.
     */
    @SuppressWarnings("unchecked")
    private String getEffect(IamPolicyResponse response) {
        Map<String, Object> policyDoc =
                (Map<String, Object>) response.getPolicyDocument();
        // Statement is stored as Map[] array not List
        Map<String, Object>[] statements =
                (Map<String, Object>[]) policyDoc.get("Statement");
        return (String) statements[0].get("Effect");
    }

    @SuppressWarnings("unchecked")
    private String getResource(IamPolicyResponse response) {
        Map<String, Object> policyDoc =
                (Map<String, Object>) response.getPolicyDocument();
        Map<String, Object>[] statements =
                (Map<String, Object>[]) policyDoc.get("Statement");
        Object resource = statements[0].get("Resource");
    
        // Resource is stored as String[] array
        if (resource instanceof String[]) {
            return ((String[]) resource)[0];
        }
        // Fallback for List<String>
        if (resource instanceof List) {
            return (String) ((List<?>) resource).get(0);
        }
        return (String) resource;
    }

    // ── Test cases ────────────────────────────────────────────

    @Test
    void handleRequest_shouldAllow_whenValidToken() {
        long futureExp = System.currentTimeMillis() / 1000 + 3600;
        String token = buildToken("user-123", "jti-abc", futureExp);

        IamPolicyResponse response = handler.handleRequest(
                buildEvent(token), context);

        assertThat(response.getPrincipalId()).isEqualTo("user-123");
        assertThat(getEffect(response)).isEqualTo("Allow");
    }

    @Test
    void handleRequest_shouldDeny_whenTokenNull() {
        IamPolicyResponse response = handler.handleRequest(
                buildEvent(null), context);

        assertThat(response.getPrincipalId()).isEqualTo("anonymous");
        assertThat(getEffect(response)).isEqualTo("Deny");
    }

    @Test
    void handleRequest_shouldDeny_whenTokenEmpty() {
        IamPolicyResponse response = handler.handleRequest(
                buildEvent(""), context);

        assertThat(getEffect(response)).isEqualTo("Deny");
    }

    @Test
    void handleRequest_shouldDeny_whenTokenExpired() {
        long pastExp = System.currentTimeMillis() / 1000 - 3600;
        String token = buildToken("user-123", "jti-abc", pastExp);

        IamPolicyResponse response = handler.handleRequest(
                buildEvent(token), context);

        assertThat(response.getPrincipalId()).isEqualTo("expired");
        assertThat(getEffect(response)).isEqualTo("Deny");
    }

    @Test
    void handleRequest_shouldDeny_whenInvalidJwtFormat() {
        IamPolicyResponse response = handler.handleRequest(
                buildEvent("not.a.valid.jwt.at.all"), context);

        assertThat(getEffect(response)).isEqualTo("Deny");
    }

    @Test
    void handleRequest_shouldDeny_whenMalformedToken() {
        IamPolicyResponse response = handler.handleRequest(
                buildEvent("onlyonepart"), context);

        assertThat(getEffect(response)).isEqualTo("Deny");
    }

    @Test
    void handleRequest_shouldAllow_withWildcardResource() {
        long futureExp = System.currentTimeMillis() / 1000 + 3600;
        String token = buildToken("user-123", "jti-abc", futureExp);

        IamPolicyResponse response = handler.handleRequest(
                buildEvent(token), context);

        // Wildcard resource — allows ALL endpoints, not just the triggering one
        // This prevents 403 when cached Allow is reused for different endpoints
        assertThat(getEffect(response)).isEqualTo("Allow");
        assertThat(getResource(response)).endsWith("*");
    }

    @Test
    void handleRequest_shouldIncludeUserId_inContext() {
        long futureExp = System.currentTimeMillis() / 1000 + 3600;
        String token = buildToken("user-sub-123", "jti-abc", futureExp);

        IamPolicyResponse response = handler.handleRequest(
                buildEvent(token), context);

        assertThat(response.getContext()).containsKey("userId");
        assertThat(response.getContext().get("userId"))
                .isEqualTo("user-sub-123");
    }
}