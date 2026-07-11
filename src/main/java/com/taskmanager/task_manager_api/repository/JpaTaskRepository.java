package com.taskmanager.task_manager_api.repository;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import com.taskmanager.task_manager_api.model.Task;
import com.taskmanager.task_manager_api.model.TaskStatus;


/**
 * Spring Data JPA backing for TaskRepository — used LOCALLY against H2.
 *
 * "!lambda" means: active whenever the "lambda" profile is NOT active.
 * So this loads automatically during local dev (mvn spring-boot:run, tests)
 * and is SKIPPED when running on AWS Lambda.
 *
 * JpaRepository auto-generates the implementation of save/findAll/findById/etc.
 * at runtime — that's the "Spring Data JPA magic" you may have heard of.
 */
@Profile("!lambda")
public interface JpaTaskRepository  extends TaskRepository,JpaRepository<Task, String>{

    @Override
    List<Task> findByStatus(TaskStatus status);
    
}
