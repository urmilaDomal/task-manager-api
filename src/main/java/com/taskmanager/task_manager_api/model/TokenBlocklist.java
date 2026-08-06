package com.taskmanager.task_manager_api.model;

import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

/**
 * Represents a revoked JWT token in the blocklist.
 *
 * When a user logs out, their token's jti (JWT ID) claim is stored here
 * with a TTL matching the token's expiry time. DynamoDB automatically
 * deletes the entry after TTL passes — no manual cleanup needed.
 *
 * On every API request, we check if the token's jti exists in this table.
 * If it does → token was revoked → return 401 Unauthorized.
 *
 * Why jti and not the full token?
 *   - jti is a short UUID string — much smaller to store than a full JWT
 *   - jti uniquely identifies each token — sufficient for revocation check
 *   - Full tokens are 800+ chars — wasteful to store thousands of them
 *
 * Why TTL?
 *   - JWT tokens expire naturally after 1 hour
 *   - After expiry, the token is already invalid — no need to keep blocking it
 *   - TTL auto-removes stale entries — blocklist stays small and cheap
 */
@DynamoDbBean
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenBlocklist {

    private String tokenId;     // JWT 'jti' claim — unique token identifier

    private String userId;      // Cognito 'sub' — who revoked this token

    private String revokedAt;   // ISO timestamp when token was revoked

    private long ttl;           // Unix epoch seconds — DynamoDB deletes after this

    @DynamoDbPartitionKey
    public String getTokenId() {
        return tokenId;
    }
}