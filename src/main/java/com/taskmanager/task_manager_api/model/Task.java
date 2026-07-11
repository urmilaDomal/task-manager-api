package com.taskmanager.task_manager_api.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import java.util.UUID;

@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@DynamoDbBean
public class Task {

     @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();
    // Builder.Default ensures DynamoDB saves always get an id even without
    // JPA's @GeneratedValue (which only fires on persist, not on DynamoDB puts)
 
    @Column(nullable = false)
    private String title;
 
    private String description;
 
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;
 
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
 
    private LocalDateTime updatedAt;

    @Column(nullable = false, updatable = false)
    private String userId;
    // Cognito 'sub' claim — set once on create, never updated.
    // Used for ownership checks: only the user who created a task
    // can read, update, or delete it.
    // 'sub' is used (not email) because it never changes even if
    // the user updates their email address in Cognito.
 
    // NOTE: createdAt/updatedAt and default status are now set explicitly in
    // TaskServiceImpl, not via @PrePersist/@PreUpdate. This ensures identical
    // behavior whether the active repository is JpaTaskRepository (H2) or
    // DynamoDbTaskRepository — @PrePersist only fires under JPA and would
    // silently no-op on DynamoDB saves, causing the bug we just fixed.
 
    @DynamoDbPartitionKey
    public String getId() {
        return id;
    }

}
