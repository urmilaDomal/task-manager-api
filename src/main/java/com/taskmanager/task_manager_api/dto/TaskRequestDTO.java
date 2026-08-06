package com.taskmanager.task_manager_api.dto;

import com.taskmanager.task_manager_api.model.TaskStatus;
import com.taskmanager.task_manager_api.util.SanitizationUtil;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

/**
 * DTO for incoming task create/update requests.
 *
 * Phase 2 addition: sanitization on setters.
 * When Spring binds the JSON request body to this DTO, it calls
 * the setters — so sanitization runs automatically before @Valid
 * validation fires. This means:
 *   1. Malicious HTML/script tags are stripped first
 *   2. Then @NotBlank/@Size validates the cleaned input
 *
 * Note: Using @Getter (not @Data) to allow custom setters.
 * Lombok's @Data generates setters that would override our custom ones.
 */
@Getter
public class TaskRequestDTO {

    @NotBlank(message = "Title is required")
    @Size(min = 1, max = 100, message = "Title must be between 1 and 100 characters")
    private String title;

    @Size(max = 500, message = "Description must be under 500 characters")
    private String description;

    private TaskStatus status;

    // ── Custom setters with sanitization ─────────────────────

    /**
     * Sanitizes title before storing — strips XSS attack vectors.
     * Called automatically by Spring when deserializing JSON request body.
     */
    public void setTitle(String title) {
        this.title = SanitizationUtil.sanitize(title);
    }

    /**
     * Sanitizes description before storing.
     */
    public void setDescription(String description) {
        this.description = SanitizationUtil.sanitize(description);
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }
}