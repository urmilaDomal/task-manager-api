package com.taskmanager.task_manager_api.exception;

public class TaskNotFoundException extends RuntimeException{

    public TaskNotFoundException(String msg){
        super(msg);
    }
}
