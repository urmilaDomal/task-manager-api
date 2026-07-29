package com.taskmanager.task_manager_api.repository;

import com.taskmanager.task_manager_api.config.DynamoDbProperties;
import com.taskmanager.task_manager_api.dto.PagedResponse;
import com.taskmanager.task_manager_api.model.Task;
import com.taskmanager.task_manager_api.model.TaskStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DynamoDB implementation of TaskRepository — used on AWS Lambda.
 *
 * Phase 1 improvements over original implementation:
 *
 * BEFORE (original):
 *   findAll() → scan entire table → filter in memory
 *   Cost: reads EVERY item regardless of userId
 *   Scale: O(n) where n = total items in table
 *
 * AFTER (Phase 1):
 *   findAllByUserId() → query userId-index GSI → only user's items
 *   findByUserIdAndStatus() → query userId-status-index GSI → pre-filtered
 *   Cost: reads only items matching the query
 *   Scale: O(k) where k = items for this specific user
 *
 * Also added:
 *   - Cursor-based pagination via DynamoDB's exclusiveStartKey
 *   - Soft delete (sets deleted=true) instead of hard delete
 *   - Filters out deleted=true items from all query results
 */
@Repository
@Profile("lambda")
@RequiredArgsConstructor
@Slf4j
public class DynamoDbTaskRepository implements TaskRepository {

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbProperties dynamoDbProperties;

    // ── Table and index accessors ─────────────────────────────

    private DynamoDbTable<Task> table() {
        return enhancedClient.table(
                dynamoDbProperties.getTableName(),
                TableSchema.fromBean(Task.class));
    }

    private DynamoDbIndex<Task> userIdIndex() {
        return table().index("userId-index");
    }

    private DynamoDbIndex<Task> userIdStatusIndex() {
        return table().index("userId-status-index");
    }

    // ── Write operations ──────────────────────────────────────

    @Override
    public Task save(Task task) {
        log.debug("Saving task id={} userId={}", task.getId(), task.getUserId());
        table().putItem(task);
        return task;
    }

    // ── Read operations ───────────────────────────────────────

    @Override
    public PagedResponse<Task> findAllByUserId(String userId, int limit, String nextToken) {
        log.debug("GSI query userId-index: userId={} limit={}", userId, limit);

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(
                        QueryConditional.keyEqualTo(
                                Key.builder().partitionValue(userId).build()))
                .limit(limit);

        // Decode the nextToken cursor back to DynamoDB's exclusiveStartKey
        if (nextToken != null) {
            requestBuilder.exclusiveStartKey(decodeToken(nextToken));
        }

        QueryEnhancedRequest request = requestBuilder.build();

        List<Task> items = new ArrayList<>();
        String responseNextToken = null;

        for (Page<Task> page : userIdIndex().query(request)) {
            // Filter out soft-deleted items — DynamoDB doesn't support
            // filter on GSI for boolean fields without a FilterExpression
            page.items().stream()
                    .filter(task -> !task.isDeleted())
                    .forEach(items::add);

            // DynamoDB returns lastEvaluatedKey when more pages exist
            if (page.lastEvaluatedKey() != null) {
                responseNextToken = encodeToken(page.lastEvaluatedKey());
            }
            break; // We only want one page per request
        }

        log.debug("GSI query returned {} items, nextToken={}", items.size(), responseNextToken);
        return PagedResponse.of(items, responseNextToken, limit);
    }

    @Override
    public PagedResponse<Task> findByUserIdAndStatus(String userId, TaskStatus status,
                                                      int limit, String nextToken) {
        log.debug("GSI query userId-status-index: userId={} status={} limit={}",
                userId, status, limit);

        // userId-status-index has userId as HASH key and status as RANGE key
        // This query says: "give me items where userId=X AND status=Y"
        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(
                        QueryConditional.keyEqualTo(
                                Key.builder()
                                        .partitionValue(userId)
                                        .sortValue(status.name())
                                        .build()))
                .limit(limit);

        if (nextToken != null) {
            requestBuilder.exclusiveStartKey(decodeToken(nextToken));
        }

        List<Task> items = new ArrayList<>();
        String responseNextToken = null;

        for (Page<Task> page : userIdStatusIndex().query(requestBuilder.build())) {
            page.items().stream()
                    .filter(task -> !task.isDeleted())
                    .forEach(items::add);

            if (page.lastEvaluatedKey() != null) {
                responseNextToken = encodeToken(page.lastEvaluatedKey());
            }
            break;
        }

        log.debug("GSI status query returned {} items", items.size());
        return PagedResponse.of(items, responseNextToken, limit);
    }

    @Override
    public Optional<Task> findById(String id) {
        log.debug("Looking up task by id={}", id);
        Task task = table().getItem(
                Key.builder().partitionValue(id).build());

        // Don't return soft-deleted items as if they exist
        if (task != null && task.isDeleted()) {
            log.debug("Task id={} is soft-deleted, returning empty", id);
            return Optional.empty();
        }
        return Optional.ofNullable(task);
    }

    @Override
    public boolean existsById(String id) {
        return findById(id).isPresent();
    }

    // ── Delete operations ─────────────────────────────────────

    @Override
    public Task softDelete(String id) {
        log.info("Soft deleting task id={}", id);
        Task task = table().getItem(Key.builder().partitionValue(id).build());
        if (task == null) {
            throw new RuntimeException("Task not found for soft delete: " + id);
        }
        task.setDeleted(true);
        task.setDeletedAt(LocalDateTime.now());
        table().putItem(task);
        log.info("Task id={} soft deleted at {}", id, task.getDeletedAt());
        return task;
    }

    @Override
    public void deleteById(String id) {
        log.warn("Hard deleting task id={} — data permanently removed", id);
        table().deleteItem(Key.builder().partitionValue(id).build());
    }

    // ── Pagination token encoding/decoding ────────────────────

    /**
     * Encodes DynamoDB's lastEvaluatedKey map into a Base64 string
     * safe to return as a URL query parameter.
     *
     * DynamoDB's exclusiveStartKey is a Map<String, AttributeValue> —
     * we serialize it to a simple "key1=val1,key2=val2" string then Base64.
     * This is opaque to the client — they just pass it back unchanged.
     */
    private String encodeToken(Map<String, AttributeValue> lastEvaluatedKey) {
        StringBuilder sb = new StringBuilder();
        lastEvaluatedKey.forEach((k, v) ->
                sb.append(k).append("=").append(v.s()).append(","));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sb.toString().getBytes());
    }

    /**
     * Decodes a client-provided nextToken back into DynamoDB's
     * exclusiveStartKey format so we can resume pagination.
     */
    private Map<String, AttributeValue> decodeToken(String token) {
        String decoded = new String(Base64.getUrlDecoder().decode(token));
        Map<String, AttributeValue> key = new HashMap<>();
        for (String pair : decoded.split(",")) {
            if (!pair.isEmpty()) {
                String[] parts = pair.split("=", 2);
                key.put(parts[0], AttributeValue.builder().s(parts[1]).build());
            }
        }
        return key;
    }

}