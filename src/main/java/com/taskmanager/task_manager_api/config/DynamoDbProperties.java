package com.taskmanager.task_manager_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds properties prefixed with "aws.dynamodb" from application-lambda.properties
 *
 * aws.dynamodb.table-name=tasks-dev  →  getTableName() returns "tasks-dev"
 */

@Component
@ConfigurationProperties(prefix = "aws.dynamodb") // maps "aws.dynamodb.*" properties
@Getter
@Setter
public class DynamoDbProperties {

    private String tableName;       // maps to aws.dynamodb.table-name
                                    // Spring auto-converts kebab-case to camelCase
}
