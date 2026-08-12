package com.taskmanager.task_manager_api.authorizer;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayCustomAuthorizerEvent;
import com.amazonaws.services.lambda.runtime.events.IamPolicyResponse;
import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.entities.Subsegment;
import com.amazonaws.xray.strategy.sampling.NoSamplingStrategy;
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
 * AWS Lambda Authorizer — validates JWT tokens at API Gateway level.
 *
 * TOKEN type — receives token via event.getAuthorizationToken()
 * Result cached 300 seconds per token by API Gateway.
 *
 * Phase 3 addition: X-Ray subsegments for blocklist check timing.
 */
@Slf4j
public class AuthorizerHandler
        implements RequestHandler<APIGatewayCustomAuthorizerEvent, IamPolicyResponse> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DynamoDbClient DYNAMO = DynamoDbClient.builder().build();

    private static final String BLOCKLIST_TABLE = System.getenv("BLOCKLIST_TABLE_NAME");
    private static final String USER_POOL_ID    = System.getenv("COGNITO_USER_POOL_ID");
    private static final String REGION          = System.getenv("AWS_REGION");

    static {
        // Configure X-Ray for Authorizer Lambda
        AWSXRay.setGlobalRecorder(
                AWSXRayRecorderBuilder.standard()
                        .withDefaultPlugins()
                        .withSamplingStrategy(new NoSamplingStrategy())
                        .build()
        );
    }

    @Override
    public IamPolicyResponse handleRequest(
            APIGatewayCustomAuthorizerEvent event, Context context) {

        // TOKEN type — token in authorizationToken field
        String token = event.getAuthorizationToken();
        String methodArn = event.getMethodArn();

        log.info("Authorizer invoked for methodArn={} tokenPresent={}",
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
                log.warn("Invalid JWT format");
                return denyPolicy("anonymous", methodArn);
            }

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode claims = MAPPER.readTree(payload);

            // ── Step 3: Check expiry ───────────────────────────
            long exp = claims.get("exp").asLong();
            long now = System.currentTimeMillis() / 1000;
            if (now > exp) {
                log.warn("Token expired at={} now={}", exp, now);
                return denyPolicy("expired", methodArn);
            }

            // ── Step 4: Check blocklist (with X-Ray subsegment) ─
            String jti = claims.has("jti") ? claims.get("jti").asText() : null;
            if (jti != null) {
                Subsegment blocklistCheck = AWSXRay.beginSubsegment("blocklist-check");
                try {
                    blocklistCheck.putMetadata("jti", jti);
                    if (isBlocklisted(jti)) {
                        log.warn("Token is blocklisted jti={}", jti);
                        blocklistCheck.putMetadata("result", "BLOCKED");
                        return denyPolicy("blocklisted", methodArn);
                    }
                    blocklistCheck.putMetadata("result", "ALLOWED");
                } finally {
                    AWSXRay.endSubsegment();
                }
            }

            // ── Step 5: Allow ──────────────────────────────────
            String userId = claims.get("sub").asText();
            log.info("Token valid for userId={}", userId);
            return allowPolicy(userId, methodArn);

        } catch (Exception e) {
            log.error("Authorizer error: {}", e.getMessage(), e);
            return denyPolicy("error", methodArn);
        }
    }

    // ── Blocklist check ───────────────────────────────────────

    private boolean isBlocklisted(String jti) {
        try {
            GetItemResponse response = DYNAMO.getItem(GetItemRequest.builder()
                    .tableName(BLOCKLIST_TABLE)
                    .key(Map.of("tokenId",
                            AttributeValue.builder().s(jti).build()))
                    .build());
            return response.hasItem() && !response.item().isEmpty();
        } catch (Exception e) {
            log.error("Blocklist check failed — failing open: {}", e.getMessage());
            return false;
        }
    }

    // ── IAM Policy builders ───────────────────────────────────

    private IamPolicyResponse allowPolicy(String principalId, String methodArn) {
        log.info("ALLOW for principalId={}", principalId);
        return IamPolicyResponse.builder()
                .withPrincipalId(principalId)
                .withPolicyDocument(IamPolicyResponse.PolicyDocument.builder()
                        .withVersion("2012-10-17")
                        .withStatement(List.of(
                                IamPolicyResponse.Statement.builder()
                                        .withEffect("Allow")
                                        .withAction("execute-api:Invoke")
                                        .withResource(List.of(methodArn))
                                        .build()))
                        .build())
                .withContext(Map.of("userId", principalId))
                .build();
    }

    private IamPolicyResponse denyPolicy(String principalId, String methodArn) {
        log.warn("DENY for principalId={}", principalId);
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