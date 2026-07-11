package com.taskmanager.task_manager_api.repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.taskmanager.task_manager_api.config.DynamoDbProperties;
import com.taskmanager.task_manager_api.model.Task;
import com.taskmanager.task_manager_api.model.TaskStatus;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;

@Repository
@Profile("lambda")
@RequiredArgsConstructor
public class DynamoDbTaskRepository implements TaskRepository{

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbProperties dynamoDbProperties;

    // Lazily resolves the table reference using the table name from properties
    private DynamoDbTable<Task> table() {
        return enhancedClient.table(dynamoDbProperties.getTableName(), TableSchema.fromBean(Task.class));
    }
 
    @Override
    public Task save(Task task) {
        table().putItem(task);
        return task;
    }
 
    @Override
    public List<Task> findAll() {
        PageIterable<Task> pages = table().scan();
        return pages.items().stream().collect(Collectors.toList());
    }
 
    @Override
    public List<Task> findByStatus(TaskStatus status) {
        // Simple approach: scan + filter in memory.
        // For production scale, you'd add a Global Secondary Index (GSI) on status instead.
        return findAll().stream()
                .filter(task -> task.getStatus() == status)
                .collect(Collectors.toList());
    }
 
    @Override
    public Optional<Task> findById(String id) {
        Task task = table().getItem(Task.builder().id(id).build());
        return Optional.ofNullable(task);
    }
 
    @Override
    public boolean existsById(String id) {
        return findById(id).isPresent();
    }
 
    @Override
    public void deleteById(String id) {
        table().deleteItem(Task.builder().id(id).build());
    }
    
}
