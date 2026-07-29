package com.taskmanager.task_manager_api.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.taskmanager.task_manager_api.dto.PagedResponse;
import com.taskmanager.task_manager_api.dto.TaskRequestDTO;
import com.taskmanager.task_manager_api.dto.TaskResponseDTO;
import com.taskmanager.task_manager_api.model.TaskStatus;
import com.taskmanager.task_manager_api.service.TaskService;
import com.taskmanager.task_manager_api.util.JwtUtil;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;




@RestController
@RequestMapping("api/v1/tasks")
@RequiredArgsConstructor
@Tag(name = "Tasks", description = "Task management endpoints")
public class TaskController {

    private final TaskService taskService;

    // ─────────────────────────────────────────────────────────
    // Every endpoint receives the Authorization header and
    // extracts the userId (Cognito 'sub') from it.
    //
    // API Gateway's Cognito Authorizer already verified the token
    // BEFORE this controller runs — so by the time we're here,
    // we know the token is valid and the userId is trustworthy.
    // We're just reading a claim, not re-verifying the signature.
    // ─────────────────────────────────────────────────────────
 
    @PostMapping
    @Operation(summary = "Create a new task")
    public ResponseEntity<TaskResponseDTO> createTask(
            @Valid @RequestBody TaskRequestDTO request,
            @RequestHeader("Authorization") String token) {
 
        String userId = JwtUtil.extractUserId(token);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(taskService.createTask(request, userId));
    }
 
    @GetMapping
    @Operation(summary = "Get paginated tasks for the authenticated user")
    public ResponseEntity<PagedResponse<TaskResponseDTO>> getAllTasks(
            @Parameter(description = "Filter by status") 
            @RequestParam(required = false) TaskStatus status,
 
            @Parameter(description = "Max items per page (default 20, max 100)")
            @RequestParam(defaultValue = "20") int limit,
 
            @Parameter(description = "Cursor from previous response for next page")
            @RequestParam(required = false) String nextToken,
 
            @RequestHeader("Authorization") String token) {
 
        String userId = JwtUtil.extractUserId(token);
        return ResponseEntity.ok(
                taskService.getAllTasks(status, userId, limit, nextToken));
    }
 
    @GetMapping("/{id}")
    @Operation(summary = "Get a task by ID — only returns if owned by the caller")
    public ResponseEntity<TaskResponseDTO> getTaskById(
            @PathVariable String id,
            @RequestHeader("Authorization") String token) {
 
        String userId = JwtUtil.extractUserId(token);
        return ResponseEntity.ok(taskService.getTaskById(id, userId));
    }
 
    @PutMapping("/{id}")
    @Operation(summary = "Update a task — only allowed if owned by the caller")
    public ResponseEntity<TaskResponseDTO> updateTask(
            @PathVariable String id,
            @Valid @RequestBody TaskRequestDTO request,
            @RequestHeader("Authorization") String token) {
 
        String userId = JwtUtil.extractUserId(token);
        return ResponseEntity.ok(taskService.updateTask(id, request, userId));
    }
 
    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete a task — sets deleted=true, row kept for audit")
    public ResponseEntity<TaskResponseDTO> deleteTask(
            @PathVariable String id,
            @RequestHeader("Authorization") String token) {
 
        String userId = JwtUtil.extractUserId(token);
        // Phase 1 change: returns 200 + deleted task (not 204 No Content)
        // This lets the client confirm WHAT was deleted and WHEN
        return ResponseEntity.ok(taskService.deleteTask(id, userId));
    }
    
}
