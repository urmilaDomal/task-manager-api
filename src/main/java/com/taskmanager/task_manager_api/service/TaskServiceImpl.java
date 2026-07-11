package com.taskmanager.task_manager_api.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.taskmanager.task_manager_api.dto.TaskRequestDTO;
import com.taskmanager.task_manager_api.dto.TaskResponseDTO;
import com.taskmanager.task_manager_api.exception.TaskAccessDeniedException;
import com.taskmanager.task_manager_api.exception.TaskNotFoundException;
import com.taskmanager.task_manager_api.model.Task;
import com.taskmanager.task_manager_api.model.TaskStatus;
import com.taskmanager.task_manager_api.repository.TaskRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskServiceImpl implements TaskService{

    private final TaskRepository taskRepository;

   // private final DynamoDbProperties dynamoDbProperties;


    // public void getTableName() {
    //     String tableName = dynamoDbProperties.getTableName(); // "tasks-dev"
    // }

    @Override
    public TaskResponseDTO createTask(TaskRequestDTO request, String userId) {
        log.info("Creating task for userId: {} with title: {}", userId, request.getTitle());
        LocalDateTime now = LocalDateTime.now();
        Task task = Task.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .status(request.getStatus() != null ? request.getStatus() : TaskStatus.TODO)
                .userId(userId)             // ← store who created it
                .createdAt(now)
                .updatedAt(now)
                .build();
        Task saved = taskRepository.save(task);
        log.info("Task created with id: {} for userId: {}", saved.getId(), userId);
        return TaskResponseDTO.from(saved);
    }
 
    @Override
    public List<TaskResponseDTO> getAllTasks(TaskStatus status, String userId) {
        log.info("Fetching tasks for userId: {}, status filter: {}", userId, status);
        List<Task> tasks = (status != null)
                ? taskRepository.findByStatus(status)
                : taskRepository.findAll();
 
        // Filter to only THIS user's tasks — never return another user's data
        return tasks.stream()
                .filter(task -> userId.equals(task.getUserId()))
                .map(TaskResponseDTO::from)
                .toList();
    }
 
    @Override
    public TaskResponseDTO getTaskById(String id, String userId) {
        log.info("Fetching task id: {} for userId: {}", id, userId);
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException("Task not found with id: " + id));
 
        // Verify ownership — throw same message as NotFound to avoid leaking
        // information about which task IDs exist (see TaskAccessDeniedException)
        verifyOwnership(task, userId);
        return TaskResponseDTO.from(task);
    }
 
    @Override
    public TaskResponseDTO updateTask(String id, TaskRequestDTO request, String userId) {
        log.info("Updating task id: {} for userId: {}", id, userId);
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException("Task not found with id: " + id));
 
        verifyOwnership(task, userId);
 
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        if (request.getStatus() != null) task.setStatus(request.getStatus());
        task.setUpdatedAt(LocalDateTime.now());
 
        Task updated = taskRepository.save(task);
        return TaskResponseDTO.from(updated);
    }
 
    @Override
    public void deleteTask(String id, String userId) {
        log.info("Deleting task id: {} for userId: {}", id, userId);
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException("Task not found with id: " + id));
 
        verifyOwnership(task, userId);
        taskRepository.deleteById(id);
    }
 
    // ─────────────────────────────────────────────────────────
    // Private helper — ownership check reused across all mutating operations
    // ─────────────────────────────────────────────────────────
    private void verifyOwnership(Task task, String userId) {
        if (!userId.equals(task.getUserId())) {
            log.warn("Ownership check failed — task: {}, requestingUser: {}, ownerUser: {}",
                    task.getId(), userId, task.getUserId());
            // Throw TaskAccessDeniedException which returns 404 (not 403)
            // to avoid confirming the resource exists to unauthorized callers
            throw new TaskAccessDeniedException(task.getId());
        }
    }
}
