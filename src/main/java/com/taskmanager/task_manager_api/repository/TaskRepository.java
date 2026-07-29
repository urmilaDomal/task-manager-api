package com.taskmanager.task_manager_api.repository;

import java.util.Optional;

import com.taskmanager.task_manager_api.dto.PagedResponse;
import com.taskmanager.task_manager_api.model.Task;
import com.taskmanager.task_manager_api.model.TaskStatus;

/**
 * Plain repository contract — NOT tied to JPA or DynamoDB.
 *
 * Two implementations exist:
 *   - JpaTaskRepository      (@Profile("!lambda") — local dev with H2)
 *   - DynamoDbTaskRepository (@Profile("lambda")   — AWS Lambda with DynamoDB)
 *
 * Spring picks whichever one matches the active profile.
 * TaskServiceImpl only ever talks to THIS interface — it doesn't know or
 * care which database is behind it.
 */
public interface TaskRepository {
 
    // Write operations
    Task save(Task task);

    // Read operations
    /**
     * Returns a page of tasks for a specific user.
     * Uses GSI: userId-index
     *
     * @param userId    Cognito sub of the requesting user
     * @param limit     max items per page (default 20)
     * @param nextToken cursor from previous page (null for first page)
     */
    PagedResponse<Task> findAllByUserId(String userId, int limit, String nextToken);

    /**
     * Returns a page of tasks filtered by userId AND status.
     * Uses GSI: userId-status-index (composite key)
     * Much more efficient than findAllByUserId + in-memory filter.
     *
     * @param userId    Cognito sub of the requesting user
     * @param status    task status to filter by
     * @param limit     max items per page
     * @param nextToken cursor from previous page
     */
    PagedResponse<Task> findByUserIdAndStatus(String userId, TaskStatus status, int limit, String nextToken);

 
    /**
     * Finds a single task by its primary key.
     * Uses the main table (not a GSI) — most efficient lookup.
     */
    Optional<Task> findById(String id);
 
    boolean existsById(String id);
 
    // ── Delete operations ─────────────────────────────────────
 
    /**
     * Soft delete — sets deleted=true and deletedAt=now().
     * The row stays in DynamoDB. All find* methods exclude deleted items.
     * Use this for all user-facing delete operations.
     */
    Task softDelete(String id);
 
    /**
     * Hard delete — permanently removes the row.
     * Only used internally or for admin cleanup.
     * NOT exposed via the REST API.
     */
    void deleteById(String id);
}