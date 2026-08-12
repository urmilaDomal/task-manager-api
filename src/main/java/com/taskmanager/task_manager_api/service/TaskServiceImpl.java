package com.taskmanager.task_manager_api.service;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Subsegment;
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
        // Custom X-Ray subsegment — groups all work done for this operation
        // Visible in X-Ray console as a named segment under the Lambda trace
        Subsegment subsegment = AWSXRay.beginSubsegment("createTask");
        try {
            subsegment.putMetadata("userId", userId);
            subsegment.putMetadata("title", request.getTitle());

            log.info("Creating task for userId={} title={}", userId, request.getTitle());
            LocalDateTime now = LocalDateTime.now();
            Task task = Task.builder()
                    .title(request.getTitle())
                    .description(request.getDescription())
                    .status(request.getStatus() != null
                            ? request.getStatus()
                            : TaskStatus.TODO)
                    .userId(userId)
                    .createdAt(now)
                    .updatedAt(now)
                    .deleted(false)
                    .build();

            Task saved = taskRepository.save(task);
            log.info("Task created id={} userId={}", saved.getId(), userId);

            subsegment.putMetadata("taskId", saved.getId());
            return TaskResponseDTO.from(saved);

        } catch (Exception e) {
            subsegment.addException(e);  // marks subsegment as faulted in X-Ray
            throw e;
        } finally {
            AWSXRay.endSubsegment();
        }
    }

    @Override
    public PagedResponse<TaskResponseDTO> getAllTasks(TaskStatus status, String userId,
                                                      int limit, String nextToken) {
        int effectiveLimit = (limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

        Subsegment subsegment = AWSXRay.beginSubsegment("getAllTasks");
        try {
            subsegment.putMetadata("userId", userId);
            subsegment.putMetadata("status", status != null ? status.name() : "ALL");
            subsegment.putMetadata("limit", effectiveLimit);
            subsegment.putMetadata("hasNextToken", nextToken != null);

            log.info("Getting tasks userId={} status={} limit={}", userId, status, effectiveLimit);

            PagedResponse<Task> taskPage;
            if (status != null) {
                taskPage = taskRepository.findByUserIdAndStatus(
                        userId, status, effectiveLimit, nextToken);
            } else {
                taskPage = taskRepository.findAllByUserId(
                        userId, effectiveLimit, nextToken);
            }

            subsegment.putMetadata("resultCount", taskPage.getItems().size());

            return PagedResponse.of(
                    taskPage.getItems().stream()
                            .map(TaskResponseDTO::from)
                            .collect(Collectors.toList()),
                    taskPage.getNextToken(),
                    effectiveLimit);

        } catch (Exception e) {
            subsegment.addException(e);
            throw e;
        } finally {
            AWSXRay.endSubsegment();
        }
    }

    @Override
    public TaskResponseDTO getTaskById(String id, String userId) {
        Subsegment subsegment = AWSXRay.beginSubsegment("getTaskById");
        try {
            subsegment.putMetadata("taskId", id);
            subsegment.putMetadata("userId", userId);

            Task task = findAndVerifyOwnership(id, userId);
            return TaskResponseDTO.from(task);

        } catch (Exception e) {
            subsegment.addException(e);
            throw e;
        } finally {
            AWSXRay.endSubsegment();
        }
    }

    @Override
    public TaskResponseDTO updateTask(String id, TaskRequestDTO request, String userId) {
        Subsegment subsegment = AWSXRay.beginSubsegment("updateTask");
        try {
            subsegment.putMetadata("taskId", id);
            subsegment.putMetadata("userId", userId);

            Task task = findAndVerifyOwnership(id, userId);
            task.setTitle(request.getTitle());
            task.setDescription(request.getDescription());
            if (request.getStatus() != null) {
                task.setStatus(request.getStatus());
            }
            task.setUpdatedAt(LocalDateTime.now());

            Task updated = taskRepository.save(task);
            log.info("Task updated id={}", id);
            return TaskResponseDTO.from(updated);

        } catch (Exception e) {
            subsegment.addException(e);
            throw e;
        } finally {
            AWSXRay.endSubsegment();
        }
    }

    @Override
    public TaskResponseDTO deleteTask(String id, String userId) {
        Subsegment subsegment = AWSXRay.beginSubsegment("deleteTask");
        try {
            subsegment.putMetadata("taskId", id);
            subsegment.putMetadata("userId", userId);

            findAndVerifyOwnership(id, userId);
            Task deleted = taskRepository.softDelete(id);
            log.info("Task soft deleted id={} deletedAt={}", id, deleted.getDeletedAt());

            subsegment.putMetadata("deletedAt", deleted.getDeletedAt().toString());
            return TaskResponseDTO.from(deleted);

        } catch (Exception e) {
            subsegment.addException(e);
            throw e;
        } finally {
            AWSXRay.endSubsegment();
        }
    }

    // ── Private helpers ───────────────────────────────────────

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