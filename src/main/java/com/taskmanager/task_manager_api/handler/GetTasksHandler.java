package com.taskmanager.task_manager_api.handler;

import com.amazonaws.serverless.exceptions.ContainerInitializationException;
import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.strategy.sampling.NoSamplingStrategy;
import com.taskmanager.task_manager_api.TaskManagerApiApplication;
import org.slf4j.MDC;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Lambda handler for GET /api/v1/tasks
 * Fault isolation — issues here don't affect other endpoints.
 * Can be scaled independently from write operations.
 */
public class GetTasksHandler implements RequestStreamHandler {

    static {
        AWSXRay.setGlobalRecorder(
                AWSXRayRecorderBuilder.standard()
                        .withDefaultPlugins()
                        .withSamplingStrategy(new NoSamplingStrategy())
                        .build());
    }

    private static final SpringBootLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse> handler;

    static {
        try {
            handler = SpringBootLambdaContainerHandler.getAwsProxyHandler(
                    TaskManagerApiApplication.class);
        } catch (ContainerInitializationException e) {
            throw new RuntimeException("Failed to initialize Spring Boot — GetTasksHandler", e);
        }
    }

    @Override
    public void handleRequest(InputStream in, OutputStream out, Context context)
            throws IOException {
        MDC.put("requestId", context.getAwsRequestId());
        MDC.put("handler", "GetTasksHandler");
        try {
            handler.proxyStream(in, out, context);
        } finally {
            MDC.clear();
        }
    }
}