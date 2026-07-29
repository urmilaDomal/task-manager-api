package com.taskmanager.task_manager_api.service;

import com.taskmanager.task_manager_api.dto.PagedResponse;
import com.taskmanager.task_manager_api.dto.TaskRequestDTO;
import com.taskmanager.task_manager_api.dto.TaskResponseDTO;
import com.taskmanager.task_manager_api.model.TaskStatus;

public interface TaskService {

    TaskResponseDTO createTask(TaskRequestDTO request, String userId);
    TaskResponseDTO getTaskById(String id, String userId);
    TaskResponseDTO updateTask(String id ,TaskRequestDTO request, String userId);
    /**
     * Soft deletes — sets deleted=true, keeps row in DynamoDB.
     * Returns the deleted task for confirmation.
     */
    TaskResponseDTO deleteTask(String id, String userId);
    /**
     * Returns paginated tasks for the given user.
     *
     * @param status    optional filter — null means all statuses
     * @param userId    Cognito sub — only return this user's tasks
     * @param limit     max items per page (default 20, max 100)
     * @param nextToken cursor from previous response (null for first page)
     */
    PagedResponse<TaskResponseDTO> getAllTasks(TaskStatus status, String userId,int limit, String nextToken);
}
