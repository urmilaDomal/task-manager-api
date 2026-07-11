package com.taskmanager.task_manager_api;

import com.amazonaws.serverless.exceptions.ContainerInitializationException;
import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.slf4j.MDC;

/**
 * Lambda entry point — bridges AWS Lambda and Spring Boot.
 *
 * Normal Spring Boot:  HTTP Request → Tomcat → DispatcherServlet → Controller
 * On Lambda:           API Gateway  → Lambda → SpringBootLambdaContainerHandler → Controller
 *                                              ↑ this class does that job
 */
public class StreamLambdaHandler implements RequestStreamHandler {

    // ─────────────────────────────────────────────────────────
    // Handler is initialized ONCE when Lambda container starts
    // (not on every request — this is the cold start)
    // ─────────────────────────────────────────────────────────
    private static final SpringBootLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse> handler;

    static {
        try {
            handler = SpringBootLambdaContainerHandler.getAwsProxyHandler(TaskManagerApiApplication.class);
            // ↑ boots your entire Spring Boot app inside Lambda
            // This runs once on cold start (~3-8 seconds)
            // Subsequent requests reuse this — much faster (~50-200ms)
        } catch (ContainerInitializationException e) {
            throw new RuntimeException("Failed to initialize Spring Boot application", e);
        }
    }

    // ─────────────────────────────────────────────────────────
    // handleRequest — called by Lambda on EVERY request
    // Lambda passes in the raw API Gateway event as a stream
    // ─────────────────────────────────────────────────────────
    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context)
            throws IOException {
        // MDC = "Mapped Diagnostic Context" — values set here automatically
        // get attached to EVERY log line printed during this request, without
        // needing to manually pass requestId into every log.info() call.
        // This is what lets you filter CloudWatch logs by one specific request.
        MDC.put("requestId", context.getAwsRequestId());
        MDC.put("functionName", context.getFunctionName());
 
        try {
            handler.proxyStream(inputStream, outputStream, context);
            // ↑ converts the Lambda event into a Spring HTTP request
            // and writes the Spring response back to the output stream
        } finally {
            // Always clear MDC after the request — Lambda may reuse this
            // thread for the NEXT request on a warm container, and we don't
            // want the previous request's ID leaking into new logs.
            MDC.clear();
        }
    }
}