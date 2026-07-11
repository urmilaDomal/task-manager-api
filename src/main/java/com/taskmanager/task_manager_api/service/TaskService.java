package com.taskmanager.task_manager_api.service;

import java.util.List;

import com.taskmanager.task_manager_api.dto.TaskRequestDTO;
import com.taskmanager.task_manager_api.dto.TaskResponseDTO;
import com.taskmanager.task_manager_api.model.TaskStatus;

public interface TaskService {

    TaskResponseDTO createTask(TaskRequestDTO request, String userId);
    List<TaskResponseDTO> getAllTasks(TaskStatus status,String userId);
    TaskResponseDTO getTaskById(String id, String userId);
    TaskResponseDTO updateTask(String id ,TaskRequestDTO request, String userId);
    void deleteTask(String id, String userId);
}
