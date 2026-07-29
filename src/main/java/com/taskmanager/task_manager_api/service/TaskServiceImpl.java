package com.taskmanager.task_manager_api.service;

import com.taskmanager.task_manager_api.dto.PagedResponse;
import com.taskmanager.task_manager_api.dto.TaskRequestDTO;
import com.taskmanager.task_manager_api.dto.TaskResponseDTO;
import com.taskmanager.task_manager_api.exception.TaskAccessDeniedException;
import com.taskmanager.task_manager_api.exception.TaskNotFoundException;
import com.taskmanager.task_manager_api.model.Task;
import com.taskmanager.task_manager_api.model.TaskStatus;
import com.taskmanager.task_manager_api.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskServiceImpl implements TaskService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final TaskRepository taskRepository;

    @Override
    public TaskResponseDTO createTask(TaskRequestDTO request, String userId) {
        log.info("Creating task for userId={} title={}", userId, request.getTitle());
        LocalDateTime now = LocalDateTime.now();
        Task task = Task.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .status(request.getStatus() != null ? request.getStatus() : TaskStatus.TODO)
                .userId(userId)
                .createdAt(now)
                .updatedAt(now)
                .deleted(false)         // explicitly not deleted on creation
                .build();
        Task saved = taskRepository.save(task);
        log.info("Task created id={} userId={}", saved.getId(), userId);
        return TaskResponseDTO.from(saved);
    }

    @Override
    public PagedResponse<TaskResponseDTO> getAllTasks(TaskStatus status, String userId,
                                                      int limit, String nextToken) {
        // Cap limit — use default if not specified, cap at max to prevent
        // clients from requesting unbounded pages that exhaust Lambda memory
        int effectiveLimit = (limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

        log.info("Getting tasks userId={} status={} limit={} hasToken={}",
                userId, status, effectiveLimit, nextToken != null);

        PagedResponse<Task> taskPage;

        if (status != null) {
            // Use userId-status-index GSI — most efficient path when status is specified
            taskPage = taskRepository.findByUserIdAndStatus(
                    userId, status, effectiveLimit, nextToken);
        } else {
            // Use userId-index GSI — all tasks for this user
            taskPage = taskRepository.findAllByUserId(
                    userId, effectiveLimit, nextToken);
        }

        // Convert Task entities to DTOs
        return PagedResponse.of(
                taskPage.getItems().stream()
                        .map(TaskResponseDTO::from)
                        .collect(Collectors.toList()),
                taskPage.getNextToken(),
                effectiveLimit);
    }

    @Override
    public TaskResponseDTO getTaskById(String id, String userId) {
        log.info("Getting task id={} userId={}", id, userId);
        Task task = findAndVerifyOwnership(id, userId);
        return TaskResponseDTO.from(task);
    }

    @Override
    public TaskResponseDTO updateTask(String id, TaskRequestDTO request, String userId) {
        log.info("Updating task id={} userId={}", id, userId);
        Task task = findAndVerifyOwnership(id, userId);

        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        if (request.getStatus() != null) task.setStatus(request.getStatus());
        task.setUpdatedAt(LocalDateTime.now());

        Task updated = taskRepository.save(task);
        log.info("Task updated id={}", id);
        return TaskResponseDTO.from(updated);
    }

    @Override
    public TaskResponseDTO deleteTask(String id, String userId) {
        log.info("Soft deleting task id={} userId={}", id, userId);
        findAndVerifyOwnership(id, userId);  // ownership check before delete

        Task deleted = taskRepository.softDelete(id);
        log.info("Task soft deleted id={} deletedAt={}", id, deleted.getDeletedAt());
        return TaskResponseDTO.from(deleted);
        // Returns the deleted task so caller can confirm:
        //   - what was deleted (title, status)
        //   - when it was deleted (deletedAt)
    }

    // ── Private helpers ───────────────────────────────────────

    /**
     * Finds a task by ID and verifies the requesting user owns it.
     * Throws TaskNotFoundException if task doesn't exist or is soft-deleted.
     * Throws TaskAccessDeniedException (→ 404) if user doesn't own it.
     *
     * Single method reused by getById, update, and delete to avoid
     * duplicating the find + ownership check logic in three places.
     */
    private Task findAndVerifyOwnership(String id, String userId) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(
                        "Task not found with id: " + id));

        if (!userId.equals(task.getUserId())) {
            log.warn("Ownership violation: task={} requestingUser={} ownerUser={}",
                    id, userId, task.getUserId());
            throw new TaskAccessDeniedException(id);
        }
        return task;
    }
}