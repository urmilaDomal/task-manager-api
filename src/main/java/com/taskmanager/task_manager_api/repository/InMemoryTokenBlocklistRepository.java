package com.taskmanager.task_manager_api.repository;

import com.taskmanager.task_manager_api.model.TokenBlocklist;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token blocklist for local dev and tests.
 * Active when NOT on lambda profile — no AWS credentials needed.
 *
 * Uses ConcurrentHashMap for thread safety.
 * Data lost on restart — acceptable for local dev.
 */
@Repository
@Profile("!lambda")
@Slf4j
public class InMemoryTokenBlocklistRepository {

    private final ConcurrentHashMap<String, TokenBlocklist> store
            = new ConcurrentHashMap<>();

    public void save(TokenBlocklist blocklist) {
        log.info("In-memory: revoking token jti={}", blocklist.getTokenId());
        store.put(blocklist.getTokenId(), blocklist);
    }

    public boolean isBlocklisted(String tokenId) {
        boolean blocklisted = store.containsKey(tokenId);
        if (blocklisted) {
            log.warn("In-memory: blocklisted token detected jti={}", tokenId);
        }
        return blocklisted;
    }
}