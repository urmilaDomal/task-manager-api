package com.taskmanager.task_manager_api.config;

import com.amazonaws.xray.interceptors.TracingInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * AWS configuration — provides DynamoDB client beans.
 * Only active on Lambda profile (@Profile("lambda")).
 *
 * Phase 3 addition: X-Ray TracingInterceptor wraps the DynamoDB client.
 * This automatically creates X-Ray subsegments for every DynamoDB call:
 *   - GetItem, PutItem, Query, Scan, DeleteItem
 * Each subsegment shows:
 *   - Table name
 *   - Operation type
 *   - Latency
 *   - Whether it was a cache hit
 *   - Error details if it failed
 *
 * In X-Ray console you'll see your full request trace:
 *   API Gateway → Lambda → DynamoDB (GetItem 12ms) → DynamoDB (PutItem 8ms)
 */
@Configuration
@Profile("lambda")
public class DynamoDbConfig {

    @Bean
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                // X-Ray TracingInterceptor automatically instruments
                // all DynamoDB SDK calls — no manual subsegment creation needed
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .addExecutionInterceptor(new TracingInterceptor())
                        .build())
                .build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }
}