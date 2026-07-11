package com.taskmanager.task_manager_api.dto;

import com.taskmanager.task_manager_api.model.TaskStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;


@Data
public class TaskRequestDTO {

    @NotBlank(message = "Title is required")
    @Size(min = 1 , max = 100 , message = "Title must be between 1 and 100 characters")
    private String title;

    @Size(max = 500 , message = "Description must be under 500 characters")
    private String description;

    private TaskStatus status;

}
