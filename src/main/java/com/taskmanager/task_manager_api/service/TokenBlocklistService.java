package com.taskmanager.task_manager_api.service;

import com.taskmanager.task_manager_api.model.TokenBlocklist;
import com.taskmanager.task_manager_api.repository.InMemoryTokenBlocklistRepository;
import com.taskmanager.task_manager_api.repository.TokenBlocklistRepository;
import com.taskmanager.task_manager_api.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Manages JWT token revocation via a DynamoDB blocklist.
 *
 * Flow:
 *   POST /auth/logout → revokeToken() → stores jti in blocklist
 *   Any request → isRevoked() → checks blocklist before processing
 *
 * Uses profile-based injection:
 *   lambda profile    → DynamoDB-backed TokenBlocklistRepository
 *   !lambda profile   → In-memory InMemoryTokenBlocklistRepository
 */
@Service
@Slf4j
public class TokenBlocklistService {

    // One of these will be injected depending on active profile
    @Autowired(required = false)
    private TokenBlocklistRepository dynamoDbRepository;

    @Autowired(required = false)
    private InMemoryTokenBlocklistRepository inMemoryRepository;

    /**
     * Revokes a JWT token by storing its jti in the blocklist.
     * TTL is set to the token's expiry time so DynamoDB auto-cleans it.
     *
     * @param token the raw JWT IdToken from the Authorization header
     * @param userId the Cognito sub of the user logging out
     */
    public void revokeToken(String token, String userId) {
        String jti = JwtUtil.extractJti(token);
        long exp = JwtUtil.extractExp(token);

        TokenBlocklist blocklist = TokenBlocklist.builder()
                .tokenId(jti)
                .userId(userId)
                .revokedAt(LocalDateTime.now(ZoneOffset.UTC).toString())
                .ttl(exp)   // DynamoDB deletes this entry after token naturally expires
                .build();

        if (dynamoDbRepository != null) {
            dynamoDbRepository.save(blocklist);
        } else if (inMemoryRepository != null) {
            inMemoryRepository.save(blocklist);
        }

        log.info("Token revoked for userId={} jti={} expires={}",
                userId, jti, Instant.ofEpochSecond(exp));
    }

    /**
     * Checks if a token has been revoked.
     *
     * @param token the raw JWT IdToken
     * @return true if the token was explicitly revoked (user logged out)
     */
    public boolean isRevoked(String token) {
        String jti = JwtUtil.extractJti(token);

        if (dynamoDbRepository != null) {
            return dynamoDbRepository.isBlocklisted(jti);
        } else if (inMemoryRepository != null) {
            return inMemoryRepository.isBlocklisted(jti);
        }
        return false;
    }
}