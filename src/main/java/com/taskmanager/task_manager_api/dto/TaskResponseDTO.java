package com.taskmanager.task_manager_api.dto;

import java.time.LocalDateTime;

import com.taskmanager.task_manager_api.model.Task;
import com.taskmanager.task_manager_api.model.TaskStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskResponseDTO {

    private String id;
    private String title;
    private String description;
    private TaskStatus status;
    private String userId;              // Cognito sub — who owns this task
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean deleted;            // true if soft-deleted
    private LocalDateTime deletedAt;    // when it was deleted (null if not deleted)
 
    // Factory method — converts Task entity to DTO
    public static TaskResponseDTO from(Task task) {
        return TaskResponseDTO.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus())
                .userId(task.getUserId())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .deleted(task.isDeleted())
                .deletedAt(task.getDeletedAt())
                .build();
    }

}
