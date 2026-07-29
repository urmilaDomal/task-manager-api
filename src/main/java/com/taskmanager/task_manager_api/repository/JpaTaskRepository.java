package com.taskmanager.task_manager_api.repository;

import com.taskmanager.task_manager_api.dto.PagedResponse;
import com.taskmanager.task_manager_api.model.Task;
import com.taskmanager.task_manager_api.model.TaskStatus;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * JPA implementation of TaskRepository — used locally with H2.
 *
 * Key design note:
 * JpaRepository already defines findById(String) returning Optional<Task>.
 * We cannot override it directly via TaskRepository because Spring Data
 * generates the implementation at runtime — we can't call super on it.
 *
 * Solution:
 *   - Let Spring Data handle findById natively (findRawById)
 *   - Override findById from TaskRepository using findRawById internally
 *   - Add deleted=false filter on top of Spring Data's result
 *
 * This interface extends BOTH TaskRepository (our contract) AND
 * JpaRepository (Spring Data's contract). Spring Data generates the
 * implementation automatically at startup.
 */
@Profile("!lambda")
public interface JpaTaskRepository extends TaskRepository, JpaRepository<Task, String> {

    // ── Spring Data auto-generated queries ────────────────────

    /**
     * Raw findById — Spring Data generates this from the method name.
     * Named differently to avoid conflict with TaskRepository.findById.
     * Used internally by our findById override below.
     */
    Optional<Task> findByIdAndDeletedFalse(String id);

    @Query("SELECT t FROM Task t WHERE t.userId = :userId AND t.deleted = false ORDER BY t.createdAt DESC")
    Slice<Task> findByUserIdNotDeleted(@Param("userId") String userId, PageRequest pageRequest);

    @Query("SELECT t FROM Task t WHERE t.userId = :userId AND t.status = :status AND t.deleted = false ORDER BY t.createdAt DESC")
    Slice<Task> findByUserIdAndStatusNotDeleted(@Param("userId") String userId,
                                                 @Param("status") TaskStatus status,
                                                 PageRequest pageRequest);

    // ── TaskRepository interface implementations ──────────────

    @Override
    default PagedResponse<Task> findAllByUserId(String userId, int limit, String nextToken) {
        int pageNumber = decodePageToken(nextToken);
        Slice<Task> slice = findByUserIdNotDeleted(userId, PageRequest.of(pageNumber, limit));
        String next = slice.hasNext() ? encodePageToken(pageNumber + 1) : null;
        return PagedResponse.of(slice.getContent(), next, limit);
    }

    @Override
    default PagedResponse<Task> findByUserIdAndStatus(String userId, TaskStatus status,
                                                       int limit, String nextToken) {
        int pageNumber = decodePageToken(nextToken);
        Slice<Task> slice = findByUserIdAndStatusNotDeleted(
                userId, status, PageRequest.of(pageNumber, limit));
        String next = slice.hasNext() ? encodePageToken(pageNumber + 1) : null;
        return PagedResponse.of(slice.getContent(), next, limit);
    }

    @Override
    default Optional<Task> findById(String id) {
        // Use Spring Data's generated method (deleted=false filter built in)
        return findByIdAndDeletedFalse(id);
    }

    @Override
    default boolean existsById(String id) {
        return findById(id).isPresent();
    }

    @Override
    default Task softDelete(String id) {
      Task task = findByIdAndDeletedFalse(id)
            .orElseThrow(() -> new RuntimeException("Task not found: " + id));
     task.setDeleted(true);
     task.setDeletedAt(LocalDateTime.now());
        return ((JpaRepository<Task, String>) this).save(task);  // ← explicit cast
    }

    @Override
    default void deleteById(String id) {
        findByIdAndDeletedFalse(id)
                .ifPresent(task -> ((JpaRepository<Task, String>) this).delete(task));
    }

    // ── Pagination token helpers ──────────────────────────────

    private static String encodePageToken(int pageNumber) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(String.valueOf(pageNumber).getBytes());
    }

    private static int decodePageToken(String token) {
        if (token == null) return 0;
        return Integer.parseInt(
                new String(Base64.getUrlDecoder().decode(token)));
    }
}