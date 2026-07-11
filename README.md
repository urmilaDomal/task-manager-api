# Task Manager API
 
A production-grade serverless REST API built with **Java 17**, **Spring Boot 3**, and **AWS** — demonstrating real-world backend engineering patterns including infrastructure as code, JWT authentication, observability, and CI/CD.
 
> **Live API:** `https://o5myciye57.execute-api.us-east-2.amazonaws.com/dev`
 
---
 
## Architecture
 
```
                         ┌─────────────────────────┐
  Client                 │     AWS Cognito          │
  (curl / Postman)  ───► │   (JWT Authentication)   │
       │                 └──────────┬──────────────┘
       │                            │ validates token
       ▼                            ▼
  ┌─────────────────────────────────────────────────┐
  │              AWS API Gateway                     │
  │   • Cognito Authorizer (401 on invalid token)   │
  │   • Rate limiting (100 req/s, burst 50)         │
  │   • CloudWatch access logging                   │
  └──────────────────────┬──────────────────────────┘
                         │ invokes (only valid requests)
                         ▼
  ┌──────────────────────────────────────────────────┐
  │              AWS Lambda                           │
  │   • Java 17 + Spring Boot 3                      │
  │   • SnapStart (reduces cold start to ~1s)        │
  │   • StreamLambdaHandler bridges Lambda ↔ Spring  │
  └──────────────────────┬───────────────────────────┘
                         │ reads/writes
                         ▼
  ┌──────────────────────────────────────────────────┐
  │              AWS DynamoDB                         │
  │   • PAY_PER_REQUEST billing                      │
  │   • Schema-less — only partition key (id) defined│
  └──────────────────────────────────────────────────┘
                         │
  ┌──────────────────────▼───────────────────────────┐
  │           AWS CloudWatch                          │
  │   • Structured JSON logs (queryable via Insights) │
  │   • Dashboard: invocations, errors, p50/p95/p99  │
  │   • Alarm: fires SNS notification if errors > 5  │
  └──────────────────────────────────────────────────┘
```
 
All infrastructure is defined as code in `template.yaml` and deployed with a single command (`sam deploy`).
 
---
 
## Tech Stack
 
| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| Cloud | AWS (Lambda, API Gateway, DynamoDB, Cognito, CloudWatch, SNS) |
| IaC | AWS SAM (CloudFormation) |
| Auth | JWT via AWS Cognito User Pool |
| Database | AWS DynamoDB (Enhanced Client SDK v2) |
| Logging | Structured JSON via Logstash Logback Encoder |
| Testing | JUnit 5, Mockito, Spring MockMvc (27 tests) |
| CI/CD | GitHub Actions with OIDC |
| Build | Maven + maven-shade-plugin (flat JAR for Lambda) |
 
---
 
## Key Design Decisions
 
### 1. Dual Repository Pattern
TaskRepository is a plain Java interface — not tied to JPA or DynamoDB. Spring's @Profile annotation activates the correct implementation:
- @Profile("!lambda") → JpaTaskRepository (H2, local dev)
- @Profile("lambda") → DynamoDbTaskRepository (DynamoDB, AWS)
 
### 2. Flat JAR for Lambda
Spring Boot repackage wraps classes in BOOT-INF/classes/ which Lambda cannot read. We scope skip=true to just the repackage execution, letting maven-shade-plugin produce a flat JAR while keeping mvn spring-boot:run working locally.
 
### 3. Structured JSON Logging
logback-spring.xml configures two profiles — human-readable text locally, JSON via LogstashEncoder on Lambda for CloudWatch Logs Insights queryability.
 
### 4. Timestamps in Service Layer
@PrePersist only fires under JPA — silently no-ops on DynamoDB saves. Moving createdAt/updatedAt into TaskServiceImpl ensures identical behavior regardless of repository.
 
### 5. Per-User Authorization
Every task stores the Cognito sub claim of its creator. The service layer filters all reads by userId and verifies ownership before mutations. Unauthorized access returns 404 not 403 to avoid leaking information about which resource IDs exist.
 
### 6. Least Privilege IAM
Lambda IAM role scoped via SAM DynamoDBCrudPolicy — only CRUD access to its own table.
 
---
 
## API Reference
 
Base URL: `https://o5myciye57.execute-api.us-east-2.amazonaws.com/dev`
 
All endpoints require: `Authorization: <Cognito IdToken>` header.
 
| Method | Path | Description | Status |
|---|---|---|---|
| POST | /api/v1/tasks | Create a task | 201 |
| GET | /api/v1/tasks | Get all tasks | 200 |
| GET | /api/v1/tasks?status=TODO | Filter by status | 200 |
| GET | /api/v1/tasks/{id} | Get task by ID | 200 |
| PUT | /api/v1/tasks/{id} | Update a task | 200 |
| DELETE | /api/v1/tasks/{id} | Delete a task | 204 |
 
### Task Status Values
TODO | IN_PROGRESS | DONE
 
### Example Request
 
```bash
curl -X POST https://o5myciye57.execute-api.us-east-2.amazonaws.com/dev/api/v1/tasks \
  -H "Authorization: YOUR_ID_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title": "Write README", "description": "Document architecture"}'
```
 
### Example Response
 
```json
{
  "id": "6a4e993b-6122-4dd6-99d1-534aaf95b9fd",
  "title": "Write README",
  "description": "Document architecture",
  "status": "TODO",
  "userId": "cognito-sub-uuid",
  "createdAt": "2026-06-21T10:15:32.123",
  "updatedAt": "2026-06-21T10:15:32.123"
}
```
 
### Error Responses
 
```json
{ "status": 400, "error": "Bad Request",           "message": "title: Title is required" }
{ "status": 401, "error": "Unauthorized",           "message": "Unauthorized" }
{ "status": 404, "error": "Not Found",              "message": "Task not found with id: abc-123" }
{ "status": 500, "error": "Internal Server Error",  "message": "An unexpected error occurred" }
```
 
---
 
## Running Locally
 
### Prerequisites
- Java 17
- Maven 3.8+
- AWS CLI + AWS SAM CLI
- Docker (for sam local)
 
### Local dev (H2 — no AWS needed)
 
```bash
mvn spring-boot:run
```
 
- API: http://localhost:8080/api/v1/tasks
- Swagger UI: http://localhost:8080/swagger-ui.html
- H2 Console: http://localhost:8080/h2-console
 
### Local Lambda emulation
 
```bash
sam local start-api --env-vars env.json --port 3001
```
 
API available at http://localhost:3001/api/v1/tasks
 
---
 
## Deployment
 
```bash
# First deploy
mvn clean package -DskipTests
sam deploy --guided
 
# Subsequent deploys
mvn clean package -DskipTests
sam deploy
 
# Get live URL and all outputs
aws cloudformation describe-stacks \
  --stack-name task-manager-api \
  --region us-east-2 \
  --query 'Stacks[0].Outputs' \
  --output table
 
# Tear down — stops all AWS charges
sam delete --stack-name task-manager-api --region us-east-2
```
 
---
 
## Testing
 
```bash
# All 27 tests
mvn test
 
# Unit tests only (service layer — Mockito, no Spring context)
mvn test -Dtest=TaskServiceTest
 
# Integration tests only (controller layer — MockMvc)
mvn test -Dtest=TaskControllerTest
```
 
**Test coverage: 27 tests**
- 11 unit tests — service business logic, per-user ownership checks
- 16 integration tests — HTTP layer, @Valid validation, authorization
 
---
 
## Observability
 
```bash
# Live log tailing
sam logs --stack-name task-manager-api --tail --region us-east-2
```
 
CloudWatch Logs Insights query:
```
fields @timestamp, level, message, requestId
| filter level = "ERROR"
| sort @timestamp desc
| limit 20
```
 
CloudWatch Dashboard:
https://us-east-2.console.aws.amazon.com/cloudwatch/home?region=us-east-2#dashboards:name=task-manager-dashboard-dev
 
---
 


---

## Resume Line

> "Built a serverless Task Manager REST API using Java 17, Spring Boot 3, AWS Lambda,
> API Gateway, DynamoDB, and Cognito — with JWT authentication, per-user authorization,
> structured CloudWatch logging, infrastructure as code via AWS SAM, rate limiting,
> and CI/CD via GitHub Actions with OIDC. Implemented a dual-repository pattern
> supporting both H2 (local) and DynamoDB (Lambda) from a single interface,
> with 27 automated tests across unit and integration layers."

---

## What I Learned

### AWS & Cloud
- **Lambda cold starts** — why JVM-based languages have slower cold starts and how SnapStart addresses this by snapshotting an initialized execution environment
- **Lambda memory = CPU** — increasing MemorySize proportionally increases CPU allocation, reducing cold start time and execution duration
- **CloudFormation intrinsic functions** — how !Ref, !Sub, and !GetAtt wire resources together at deploy time without hardcoding values
- **IAM least privilege** — scoping Lambda permissions via SAM DynamoDBCrudPolicy instead of broad account-level access
- **OIDC in CI/CD** — why short-lived OIDC tokens are safer than storing long-lived AWS access keys as GitHub secrets
- **PAY_PER_REQUEST DynamoDB** — no capacity planning, scales automatically, costs nothing when idle

### Spring Boot & Java
- **Dual repository pattern** — @Profile activates different implementations of the same interface per environment
- **Flat JAR vs BOOT-INF** — Spring Boot repackaging wraps classes in BOOT-INF/classes/ which Lambda cannot read
- **@PrePersist silently fails on DynamoDB** — JPA lifecycle hooks only fire under Hibernate; timestamps moved to service layer
- **MDC for request tracing** — setting requestId in MDC once per request attaches it to every log line automatically

### Security
- **Authentication vs Authorization** — Cognito JWT proves who you are; ownership checks prove you are allowed to do this specific thing
- **404 vs 403 for ownership failures** — returning 403 confirms the resource exists; 404 gives nothing away
- **JWT sub vs email** — sub is Cognito immutable UUID; email can change; sub used for ownership checks
- **API Gateway validates JWT before Lambda runs** — invalid tokens rejected at gateway, saving Lambda compute cost

### Debugging Real Problems
- **Spring Boot 4 vs 3.2.5 conflict** — aws-serverless-java-container built for Boot 3.x caused ClassNotFoundException on Boot 4
- **BOOT-INF/classes vs flat JAR** — first Lambda deployment failed because shade plugin ran after Spring Boot repackage
- **samconfig.toml caching bad values** — wrong Environment value kept being reused on every sam deploy
- **Duplicate @ExceptionHandler** — merging generated code created two handlers for MethodArgumentNotValidException
