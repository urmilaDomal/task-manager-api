package com.taskmanager.task_manager_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.taskmanager.task_manager_api.config.DynamoDbProperties;

@SpringBootApplication
@EnableConfigurationProperties(DynamoDbProperties.class)		// registers the config class
public class TaskManagerApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(TaskManagerApiApplication.class, args);
	}

}
