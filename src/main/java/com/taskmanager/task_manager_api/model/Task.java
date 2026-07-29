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
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

import java.util.UUID;


/**
 * Dual-purpose entity:
 *   - JPA annotations (@Entity, @Id, @Column) → local H2 dev
 *   - DynamoDB annotations (@DynamoDbBean etc.) → AWS Lambda
 *
 * Phase 1 additions:
 *   - deleted (boolean) — soft delete flag
 *   - deletedAt (LocalDateTime) — when it was deleted
 *
 * Soft delete means DELETE /tasks/{id} sets deleted=true and
 * deletedAt=now() instead of physically removing the row.
 * All queries automatically exclude deleted=true items.
 * This gives us:
 *   - Audit trail — we know what was deleted and when
 *   - Recovery — can restore accidentally deleted tasks
 *   - Compliance — data retained for regulatory requirements
 */
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
 
     // ── Soft delete fields (Phase 1) ─────────────────────────
     @Builder.Default
     @Column(nullable = false)
     private boolean deleted = false;    // true = logically deleted, excluded from all queries
  
     private LocalDateTime deletedAt;    // when the task was soft-deleted (null if not deleted)
     
    @DynamoDbPartitionKey
    public String getId() {
        return id;
    }

    // userId is partition key for BOTH GSIs
    @DynamoDbSecondaryPartitionKey(indexNames = {"userId-index", "userId-status-index"})
    public String getUserId() { return userId; }

    // status is sort key for userId-status-index
    @DynamoDbSecondarySortKey(indexNames = {"userId-status-index"})
    public TaskStatus getStatus() { return status; }
}
