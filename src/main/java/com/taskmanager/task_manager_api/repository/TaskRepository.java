package com.taskmanager.task_manager_api.repository;

import java.util.List;
import java.util.Optional;

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
 
    Task save(Task task);
 
    List<Task> findAll();
 
    List<Task> findByStatus(TaskStatus status);
 
    Optional<Task> findById(String id);
 
    boolean existsById(String id);
 
    void deleteById(String id);
}