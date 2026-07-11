package com.taskmanager.task_manager_api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Provides the AWS SDK clients needed by DynamoDbTaskRepository.
 * Only created when the "lambda" profile is active — locally, none of this
 * runs, avoiding any need for AWS credentials during local dev or tests.
 */
@Configuration
@Profile("lambda")
public class DynamoDbConfig {
 
      @Bean
    public DynamoDbClient dynamoDbClient() {
        // Reads region + credentials automatically from the Lambda execution
        // environment (IAM role attached via template.yaml's DynamoDBCrudPolicy)
        return DynamoDbClient.builder().build();
    }
 
    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }
}
