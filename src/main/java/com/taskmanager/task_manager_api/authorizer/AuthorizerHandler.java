package com.taskmanager.task_manager_api.authorizer;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayCustomAuthorizerEvent;
import com.amazonaws.services.lambda.runtime.events.IamPolicyResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * AWS Lambda Authorizer — TOKEN type.
 * Validates JWT expiry + checks token blocklist.
 *
 * Phase 3 X-Ray removed from Authorizer — causes SegmentNotFoundException
 * under concurrent load when multiple requests hit simultaneously.
 * X-Ray tracing still active on main Lambda functions.
 */
@Slf4j
public class AuthorizerHandler
        implements RequestHandler<APIGatewayCustomAuthorizerEvent, IamPolicyResponse> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DynamoDbClient DYNAMO = DynamoDbClient.builder().build();

    private static final String BLOCKLIST_TABLE = System.getenv("BLOCKLIST_TABLE_NAME");
    @SuppressWarnings("unused")
    private static final String USER_POOL_ID    = System.getenv("COGNITO_USER_POOL_ID");

    @Override
    public IamPolicyResponse handleRequest(
            APIGatewayCustomAuthorizerEvent event, Context context) {

        // TOKEN type — token in authorizationToken field
        String token = event.getAuthorizationToken();
        String methodArn = event.getMethodArn();

        log.info("Authorizer invoked methodArn={} tokenPresent={}",
                methodArn, token != null);

        try {
            // ── Step 1: Basic format check ─────────────────────
            if (token == null || token.isEmpty()) {
                log.warn("No token provided");
                return denyPolicy("anonymous", methodArn);
            }

            // ── Step 2: Decode JWT payload ─────────────────────
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                log.warn("Invalid JWT format — expected 3 parts");
                return denyPolicy("anonymous", methodArn);
            }

            // Add padding if needed for Base64 decoding
            String payloadBase64 = parts[1];
            int padding = 4 - payloadBase64.length() % 4;
            if (padding != 4) {
                payloadBase64 = payloadBase64 + "=".repeat(padding);
            }

            String payload = new String(Base64.getUrlDecoder().decode(payloadBase64));
            JsonNode claims = MAPPER.readTree(payload);

            // ── Step 3: Check expiry ───────────────────────────
            if (!claims.has("exp")) {
                log.warn("Token missing exp claim");
                return denyPolicy("invalid", methodArn);
            }

            long exp = claims.get("exp").asLong();
            long now = System.currentTimeMillis() / 1000;
            if (now > exp) {
                log.warn("Token expired exp={} now={}", exp, now);
                return denyPolicy("expired", methodArn);
            }

            // ── Step 4: Check blocklist ────────────────────────
            String jti = claims.has("jti") ? claims.get("jti").asText() : null;
            if (jti != null && isBlocklisted(jti)) {
                log.warn("Token blocklisted jti={}", jti);
                return denyPolicy("blocklisted", methodArn);
            }

            // ── Step 5: Allow ──────────────────────────────────
            String userId = claims.has("sub") ? claims.get("sub").asText() : "unknown";
            log.info("ALLOW userId={}", userId);
            return allowPolicy(userId, methodArn);

        } catch (Exception e) {
            log.error("Authorizer error: {}", e.getMessage(), e);
            return denyPolicy("error", methodArn);
        }
    }

    // ── Blocklist check ───────────────────────────────────────

    private boolean isBlocklisted(String jti) {
        try {
            if (BLOCKLIST_TABLE == null || BLOCKLIST_TABLE.isEmpty()) {
                log.warn("BLOCKLIST_TABLE_NAME not set — skipping blocklist check");
                return false;
            }
            GetItemResponse response = DYNAMO.getItem(GetItemRequest.builder()
                    .tableName(BLOCKLIST_TABLE)
                    .key(Map.of("tokenId",
                            AttributeValue.builder().s(jti).build()))
                    .build());
            return response.hasItem() && !response.item().isEmpty();
        } catch (Exception e) {
            // Fail open — allow request if blocklist check fails
            // Prevents DynamoDB outage from blocking all users
            log.error("Blocklist check failed — failing open: {}", e.getMessage());
            return false;
        }
    }

    // ── IAM Policy builders ───────────────────────────────────

    private IamPolicyResponse allowPolicy(String principalId, String methodArn) {
        // Allow all methods on this API — not just the specific method
        // This prevents issues when Authorizer result is cached
        // and used for different endpoints
        String arnPrefix = methodArn.substring(0,
                methodArn.lastIndexOf('/') + 1) + "*";

        return IamPolicyResponse.builder()
                .withPrincipalId(principalId)
                .withPolicyDocument(IamPolicyResponse.PolicyDocument.builder()
                        .withVersion("2012-10-17")
                        .withStatement(List.of(
                                IamPolicyResponse.Statement.builder()
                                        .withEffect("Allow")
                                        .withAction("execute-api:Invoke")
                                        .withResource(List.of(arnPrefix))
                                        .build()))
                        .build())
                .withContext(Map.of("userId", principalId))
                .build();
    }

    private IamPolicyResponse denyPolicy(String principalId, String methodArn) {
        log.warn("DENY principalId={}", principalId);
        return IamPolicyResponse.builder()
                .withPrincipalId(principalId)
                .withPolicyDocument(IamPolicyResponse.PolicyDocument.builder()
                        .withVersion("2012-10-17")
                        .withStatement(List.of(
                                IamPolicyResponse.Statement.builder()
                                        .withEffect("Deny")
                                        .withAction("execute-api:Invoke")
                                        .withResource(List.of(methodArn))
                                        .build()))
                        .build())
                .build();
    }
}