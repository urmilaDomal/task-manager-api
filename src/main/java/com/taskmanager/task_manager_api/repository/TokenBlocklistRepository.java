package com.taskmanager.task_manager_api.repository;

import com.taskmanager.task_manager_api.config.DynamoDbProperties;
import com.taskmanager.task_manager_api.model.TokenBlocklist;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

/**
 * DynamoDB repository for revoked JWT tokens.
 *
 * Only active on Lambda profile — locally we use an in-memory
 * blocklist (InMemoryTokenBlocklistRepository) to avoid needing
 * AWS credentials for local dev/testing.
 */
@Repository
@Profile("lambda")
@RequiredArgsConstructor
@Slf4j
public class TokenBlocklistRepository {

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbProperties dynamoDbProperties;

    private DynamoDbTable<TokenBlocklist> table() {
        return enhancedClient.table(
                dynamoDbProperties.getBlocklistTableName(),
                TableSchema.fromBean(TokenBlocklist.class));
    }

    /**
     * Saves a revoked token to the blocklist.
     * DynamoDB TTL will auto-delete it after the token naturally expires.
     */
    public void save(TokenBlocklist blocklist) {
        log.info("Revoking token jti={} for userId={}", blocklist.getTokenId(), blocklist.getUserId());
        table().putItem(blocklist);
    }

    /**
     * Checks if a token has been revoked.
     *
     * @param tokenId the JWT 'jti' claim
     * @return true if the token is blocklisted (revoked)
     */
    public boolean isBlocklisted(String tokenId) {
        TokenBlocklist item = table().getItem(
                Key.builder().partitionValue(tokenId).build());
        boolean blocklisted = item != null;
        if (blocklisted) {
            log.warn("Blocklisted token detected jti={}", tokenId);
        }
        return blocklisted;
    }
}