package com.taskmanager.task_manager_api.exception;

/**
 * Thrown when an authenticated user tries to access a task
 * they don't own. Maps to 403 Forbidden in GlobalExceptionHandler.
 *
 * Deliberately vague message ("not found" rather than "access denied")
 * to avoid leaking information about which task IDs exist —
 * telling an attacker "you don't have access" confirms the ID exists,
 * while "not found" gives nothing away.
 *
 * This security pattern is called "security through obscurity at the
 * resource level" — a real-world API design decision worth mentioning
 * in interviews.
 */

public class TaskAccessDeniedException extends RuntimeException{
 
    public TaskAccessDeniedException(String taskId) {
        super("Task not found with id: " + taskId);
        // Intentionally same message as TaskNotFoundException —
        // see class-level javadoc for the reasoning
    }
}
