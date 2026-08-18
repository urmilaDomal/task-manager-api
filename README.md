# Task Manager API

A production-grade serverless REST API built with **Java 17**, **Spring Boot 3**, and **AWS** — demonstrating real-world backend engineering patterns including infrastructure as code, JWT authentication, Lambda Authorizers, distributed tracing, per-endpoint Lambda functions, and CI/CD.

> **Live API:** `https://YOUR-API-URL.execute-api.us-east-2.amazonaws.com/dev`

---

## Architecture

```
                    ┌─────────────────────────┐
  Client            │     AWS Cognito         │
  (curl/Postman)───►│   AdminCreateUserOnly   │──► issues JWT on login
       │            └─────────────────────────┘
       │
       ▼
  ┌─────────────────────────────────────────────────────┐
  │              AWS API Gateway                        │
  │   • Rate limiting (100 req/s, burst 50)             │
  │   • CloudWatch access logging                       │
  │   • Usage plan + API key                            │
  └──────────────────────┬──────────────────────────────┘
                         │
                         ▼
  ┌──────────────────────────────────────────────────────┐
  │         Lambda Authorizer (Phase 2)                  │
  │   • TOKEN type — validates JWT signature             │
  │   • Checks token expiry                              │
  │   • Checks token blocklist (revoked tokens)          │
  │   • Returns Allow/Deny IAM policy                    │
  │   • Result cached 300s — minimal cold start impact   │
  └──────────────────────┬───────────────────────────────┘
                         │ Allow only — routes to specific function
                         ▼
  ┌──────────────────────────────────────────────────────────────────┐
  │              Per-endpoint Lambda Functions (Phase 4)             │
  │                                                                  │
  │  GetTasksFunction   → GET  /api/v1/tasks                         │
  │  GetTaskFunction    → GET  /api/v1/tasks/{id}                    │
  │  CreateTaskFunction → POST /api/v1/tasks                         │
  │  UpdateTaskFunction → PUT  /api/v1/tasks/{id}                    │
  │  DeleteTaskFunction → DELETE /api/v1/tasks/{id}                  │
  │  LogoutFunction     → POST /api/v1/auth/logout                   │
  │                                                                  │
  │  Each function: Java 17 + Spring Boot 3 + SnapStart (~1s cold)   │
  │  Fault isolation — bug in DELETE never affects GET               │
  │  Independent scaling per endpoint                                │
  └──────────────────────┬───────────────────────────────────────────┘
                         │
              ┌──────────┴──────────┐
              ▼                     ▼
  ┌──────────────────┐   ┌─────────────────────────┐
  │   DynamoDB       │   │  Token Blocklist        │
  │   tasks-dev      │   │  token-blocklist-dev    │
  │                  │   │                         │
  │  GSI: userId     │   │  TTL auto-cleanup       │
  │  GSI: userId+    │   │  jti stored (not token) │
  │       status     │   │                         │
  │  Soft delete     │   │                         │
  │  Cursor paging   │   │                         │
  └──────────────────┘   └─────────────────────────┘
              │
              ▼
  ┌──────────────────────────────────────────────────────┐
  │           AWS CloudWatch + X-Ray (Phase 3)           │
  │   • Structured JSON logs (queryable via Insights)    │
  │   • X-Ray traces across all 7 Lambda functions       │
  │   • Dashboard: per-endpoint invocations/errors/p95   │
  │   • Alarm: SNS notification if errors > 5/5min       │
  └──────────────────────────────────────────────────────┘
```

All infrastructure defined as code in `template.yaml` — deployed with `sam deploy`.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| Cloud | AWS (Lambda, API Gateway, DynamoDB, Cognito, CloudWatch, X-Ray, SNS) |
| IaC | AWS SAM (CloudFormation) |
| Auth | JWT via AWS Cognito + Lambda Authorizer (TOKEN type) |
| Database | AWS DynamoDB (Enhanced Client SDK v2) with GSIs |
| Tracing | AWS X-Ray with custom subsegments per operation |
| Logging | Structured JSON via Logstash Logback Encoder + MDC |
| Testing | JUnit 5, Mockito, Spring MockMvc (63 tests) |
| CI/CD | GitHub Actions with OIDC |
| Build | Maven + maven-shade-plugin (flat JAR for Lambda) |
| Load Testing | k6 — 50 concurrent users |

---

## Project Structure

```
task-manager-api/
├── template.yaml                                    # SAM — entire AWS infrastructure
├── load-test.js                                     # k6 load test script
├── pom.xml
└── src/
    ├── main/java/com/taskmanager/
    │   ├── authorizer/
    │   │   └── AuthorizerHandler.java               # Lambda Authorizer (Phase 2)
    │   ├── config/
    │   │   ├── DynamoDbConfig.java                  # X-Ray TracingInterceptor (Phase 3)
    │   │   └── DynamoDbProperties.java
    │   ├── controller/
    │   │   ├── AuthController.java                  # POST /auth/logout (Phase 2)
    │   │   └── TaskController.java
    │   ├── dto/
    │   │   ├── PagedResponse.java                   # Pagination wrapper (Phase 1)
    │   │   ├── TaskRequestDTO.java                  # XSS sanitization on setters (Phase 2)
    │   │   └── TaskResponseDTO.java
    │   ├── handler/                                 # Per-endpoint Lambda handlers (Phase 4)
    │   │   ├── GetTasksHandler.java
    │   │   ├── GetTaskHandler.java
    │   │   ├── CreateTaskHandler.java
    │   │   ├── UpdateTaskHandler.java
    │   │   ├── DeleteTaskHandler.java
    │   │   └── LogoutHandler.java
    │   ├── model/
    │   │   ├── Task.java                            # Dual JPA/DynamoDB + GSI annotations
    │   │   ├── TaskStatus.java
    │   │   └── TokenBlocklist.java                  # JWT revocation entity (Phase 2)
    │   ├── repository/
    │   │   ├── TaskRepository.java                  # Plain interface (DB-agnostic)
    │   │   ├── JpaTaskRepository.java               # H2 @Profile("!lambda")
    │   │   ├── DynamoDbTaskRepository.java          # DynamoDB @Profile("lambda") + GSIs
    │   │   ├── TokenBlocklistRepository.java        # DynamoDB blocklist (Phase 2)
    │   │   └── InMemoryTokenBlocklistRepository.java # Local dev (Phase 2)
    │   ├── service/
    │   │   ├── TaskService.java
    │   │   ├── TaskServiceImpl.java                 # X-Ray subsegments (Phase 3)
    │   │   └── TokenBlocklistService.java           # Revoke/check tokens (Phase 2)
    │   ├── util/
    │   │   ├── JwtUtil.java                         # Extract sub/jti/exp claims
    │   │   └── SanitizationUtil.java                # XSS prevention (Phase 2)
    │   └── StreamLambdaHandler.java                 # X-Ray recorder config (Phase 3)
    └── test/java/com/taskmanager/
        ├── controller/
        │   ├── AuthControllerTest.java              # 3 tests
        │   └── TaskControllerTest.java              # 16 tests
        ├── dto/
        │   └── TaskRequestDTOTest.java              # 6 tests
        ├── service/
        │   ├── TaskServiceTest.java                 # 13 tests
        │   └── TokenBlocklistServiceTest.java       # 5 tests
        └── util/
            ├── JwtUtilTest.java                     # 6 tests
            └── SanitizationUtilTest.java            # 13 tests
```

---

## Phases

### Phase 1 — Data Layer

**Gap: DynamoDB O(n) full table scan**
- Added `userId-index` GSI — queries tasks by userId directly (O(k) not O(n))
- Added `userId-status-index` GSI — composite key for status filtering without scanning
- GSIs must be added one at a time to existing DynamoDB tables — adding both simultaneously throws `Cannot perform more than one GSI creation in a single update`

**Gap: No pagination**
- `GET /tasks` now returns `PagedResponse<T>` with `items`, `count`, `limit`, `nextToken`
- Cursor-based pagination via DynamoDB's `lastEvaluatedKey` encoded as Base64 token
- Offset pagination (`LIMIT x OFFSET y`) breaks under concurrent writes — cursor pagination doesn't
- `DEFAULT_LIMIT=20`, `MAX_LIMIT=100` enforced via `Math.min(limit, MAX_LIMIT)`

**Gap: Hard delete — data lost forever**
- `DELETE /tasks/{id}` sets `deleted=true` and `deletedAt=now()` instead of removing row
- All queries filter `deleted=false` — soft-deleted tasks invisible to users
- Returns `200 OK` with deleted task body so caller confirms what was deleted and when
- Gives audit trail, recovery capability, compliance data retention

---

### Phase 2 — Security

**Gap: Open self-registration**
- `AdminCreateUserOnly: true` in Cognito User Pool
- Users created only via `admin-create-user` + `admin-set-user-password`
- Prevents anonymous account creation and API abuse

**Gap: JWT can't be revoked before expiry**
- `POST /api/v1/auth/logout` extracts `jti` from JWT, stores in `TokenBlocklistTable`
- Lambda Authorizer checks blocklist on every request before main Lambda runs
- `jti` stored (not full token) — UUID vs 800-char string; TTL matches token `exp` for auto-cleanup
- Revoked token denied within 300 seconds (Authorizer cache TTL)

**Gap: XSS input sanitization**
- `SanitizationUtil.sanitize()` strips script tags, event handlers, javascript: protocol, HTML tags
- Called in `TaskRequestDTO` custom setters — fires automatically before `@Valid` validation
- No risk of forgetting to sanitize at the call site — DTO setters guarantee it

**Lambda Authorizer (TOKEN type)**
- Separate lightweight Lambda invoked by API Gateway before main Lambda
- TOKEN type — receives token via `event.getAuthorizationToken()` (not headers)
- Validates JWT expiry + checks token blocklist in DynamoDB
- Result cached 300s per token — Authorizer runs at most once per 5 minutes per user
- Why TOKEN over REQUEST: authorization decision based only on JWT, not request context

---

### Phase 3 — Observability

**AWS X-Ray distributed tracing**
- `Tracing: Active` in SAM Globals — X-Ray enabled on all Lambda functions
- `TracingInterceptor` wraps DynamoDB client — auto-creates subsegments for every operation
- Custom subsegments per service operation with userId/taskId metadata
- `NoSamplingStrategy` — traces 100% of requests in dev
- X-Ray Trace Map shows: Client → API Gateway → Authorizer Lambda → Main Lambda → DynamoDB

**Structured logging + MDC**
- Logstash Logback Encoder — every log line is JSON (queryable via CloudWatch Insights)
- MDC `requestId` set per request — trace one request through all log lines
- MDC `handler` set per Lambda function — identify which function handled each request

---

### Phase 4 — Architecture

**Gap: Single Lambda blast radius**
- `task-manager-dev` replaced by 6 dedicated per-endpoint Lambda functions
- Bug in `DeleteTaskFunction` never affects `GetTasksFunction`
- Each function has independent CloudWatch metrics and log group
- Different memory allocation possible per endpoint (logout uses 256MB vs 512MB for task functions)

**Gap: Cold start p99 spikes**
- SnapStart on all 6 functions — snapshots initialized JVM state
- Reduces cold start from ~8s to ~1s per function
- Requires `AWS::Lambda::Version` resource per function for activation

**Gap: API versioning**
- URL path strategy: `/api/v1/tasks` — breaking changes go to `/api/v2/`
- Visible in logs, API Gateway, curl — no hidden headers or query params
- Different versions can route to different Lambda functions independently

---

## Key Design Decisions

### 1. Dual repository pattern
`TaskRepository` is a plain Java interface — Spring's `@Profile` activates the correct implementation:
- `@Profile("!lambda")` → `JpaTaskRepository` (H2, local dev)
- `@Profile("lambda")` → `DynamoDbTaskRepository` (DynamoDB, AWS)

Service layer never knows which database is underneath.

### 2. Lambda Authorizer over Cognito Authorizer
Cognito Authorizer validates JWT signature/expiry only. Lambda Authorizer adds:
- Token blocklist check — enabling real logout/revocation
- Custom validation logic (extensible for IP allowlists, custom claims)
- Result cached 5 minutes — minimal extra latency

### 3. GSI-based queries over full table scan
Original `findAll()` scanned the entire table (O(n)). Phase 1 adds two GSIs:
- `userId-index` — queries tasks by userId directly
- `userId-status-index` — composite key for status filtering

### 4. Cursor-based pagination over offset
DynamoDB's `lastEvaluatedKey` used as the nextToken cursor. Offset pagination breaks when items are inserted/deleted between page requests — cursor pagination doesn't.

### 5. Soft delete
`DELETE /tasks/{id}` sets `deleted=true` instead of removing the row. Gives audit trail, recovery, and compliance data retention.

### 6. Input sanitization at DTO setter level
`TaskRequestDTO` setters call `SanitizationUtil.sanitize()` before `@Valid` runs. XSS is stripped automatically when Spring deserializes the JSON request body.

### 7. JWT TTL on blocklist entries
Blocklist entries have a DynamoDB TTL matching the token's `exp` claim. After natural expiry, the entry auto-deletes — blocklist stays small and cost-free.

### 8. Flat JAR for Lambda
Spring Boot's default repackage wraps classes in `BOOT-INF/classes/` which Lambda can't read. `maven-shade-plugin` produces the flat JAR Lambda needs while keeping `mvn spring-boot:run` working locally.

### 9. Timestamps in service layer not JPA hooks
`@PrePersist` only fires under JPA — silently no-ops on DynamoDB. Moving `createdAt`/`updatedAt` to `TaskServiceImpl` ensures identical behavior regardless of which repository is active.

### 10. Per-endpoint Lambda with SnapStart
One Spring Boot context per function — same code, different entry point. Fault isolation at the handler level. SnapStart reduces JVM cold starts from ~8s to ~1s.

---

## API Reference

Base URL: `https://YOUR-API-URL.execute-api.us-east-2.amazonaws.com/dev`

All endpoints require: `Authorization: <Cognito IdToken>`

### Auth

| Method | Path | Description | Status |
|---|---|---|---|
| `POST` | `/api/v1/auth/logout` | Revoke current token immediately | `200 OK` |

### Tasks

| Method | Path | Description | Status |
|---|---|---|---|
| `POST` | `/api/v1/tasks` | Create a task | `201 Created` |
| `GET` | `/api/v1/tasks` | Get paginated tasks | `200 OK` |
| `GET` | `/api/v1/tasks?status=TODO` | Filter by status (GSI query) | `200 OK` |
| `GET` | `/api/v1/tasks?limit=10` | Page size (default 20, max 100) | `200 OK` |
| `GET` | `/api/v1/tasks?nextToken=xyz` | Next page cursor | `200 OK` |
| `GET` | `/api/v1/tasks/{id}` | Get task by ID | `200 OK` |
| `PUT` | `/api/v1/tasks/{id}` | Update a task | `200 OK` |
| `DELETE` | `/api/v1/tasks/{id}` | Soft delete (deleted=true, row kept) | `200 OK` |

### Paginated response shape

```json
{
  "items": [...],
  "count": 2,
  "limit": 20,
  "nextToken": "eyJ..."
}
```

`nextToken: null` means no more pages.

### Error responses

```json
{ "status": 400, "error": "Bad Request",          "message": "title: Title is required" }
{ "status": 401, "error": "Unauthorized",          "message": "Unauthorized" }
{ "status": 404, "error": "Not Found",             "message": "Task not found with id: abc" }
{ "status": 500, "error": "Internal Server Error", "message": "An unexpected error occurred" }
```

---

## Running Locally

### Prerequisites
Java 17, Maven 3.8+, AWS CLI, AWS SAM CLI, Docker

### Local dev (H2 — no AWS needed)

```bash
mvn spring-boot:run
```

- API: `http://localhost:8080/api/v1/tasks`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- H2 Console: `http://localhost:8080/h2-console`

### Local fake JWT

```bash
HEADER=$(echo -n '{"alg":"RS256"}' | base64 | tr '+/' '-_' | tr -d '=')
PAYLOAD=$(echo -n '{"sub":"local-user-123","jti":"test-jti","exp":9999999999}' | base64 | tr '+/' '-_' | tr -d '=')
export FAKE_JWT="$HEADER.$PAYLOAD.fakesig"
```

---

## Deployment

```bash
# First deploy
mvn clean package -DskipTests && sam deploy --guided

# Subsequent deploys (CI/CD auto-deploys on push to main)
mvn clean package -DskipTests && sam deploy

# Get all outputs
aws cloudformation describe-stacks \
  --stack-name task-manager-api \
  --region us-east-2 \
  --query 'Stacks[0].Outputs' \
  --output table
```

### Important — DynamoDB table recreation
If you delete the stack and redeploy, manually delete retained DynamoDB tables first:
```bash
aws dynamodb delete-table --table-name tasks-dev --region us-east-2
aws dynamodb delete-table --table-name token-blocklist-dev --region us-east-2
aws dynamodb wait table-not-exists --table-name tasks-dev --region us-east-2
```
`DeletionPolicy: Retain` protects data from accidental stack deletion but causes `EarlyValidation::ResourceExistenceCheck` errors if tables exist when CloudFormation tries to create them.

---

## Testing

```bash
# All 63 tests
mvn test

# By layer
mvn test -Dtest=TaskServiceTest           # 13 unit — service layer
mvn test -Dtest=TaskControllerTest        # 16 integration — HTTP layer
mvn test -Dtest=TokenBlocklistServiceTest # 5 unit — token revocation
mvn test -Dtest=AuthControllerTest        # 3 integration — auth endpoints
mvn test -Dtest=SanitizationUtilTest      # 13 unit — XSS prevention
mvn test -Dtest=JwtUtilTest               # 6 unit — JWT claim extraction
mvn test -Dtest=TaskRequestDTOTest        # 6 unit — DTO sanitization
```

**Test coverage: 63 tests**

| Test Class | Count | What it covers |
|---|---|---|
| `TaskServiceTest` | 13 | Service business logic, ownership, pagination |
| `TaskControllerTest` | 16 | HTTP layer, request/response shape |
| `TokenBlocklistServiceTest` | 5 | Token revocation, jti extraction |
| `AuthControllerTest` | 3 | Logout endpoint |
| `SanitizationUtilTest` | 13 | XSS stripping — normal and malicious inputs |
| `JwtUtilTest` | 6 | JWT claim extraction — sub, jti, exp |
| `TaskRequestDTOTest` | 6 | Sanitization fires in DTO setters |

---

## Observability

```bash
# Live log tailing — all functions
sam logs --stack-name task-manager-api --tail --region us-east-2

# Specific function
sam logs --stack-name task-manager-api --name GetTasksFunction --tail --region us-east-2

# Authorizer logs
sam logs --stack-name task-manager-api --name AuthorizerFunction --tail --region us-east-2
```

### CloudWatch Logs Insights queries

```sql
-- Error analysis across all functions
fields @timestamp, level, message, requestId, handler
| filter level = "ERROR"
| sort @timestamp desc
| limit 20

-- Latency by operation
fields @timestamp, @duration
| filter @message like /END/
| stats avg(@duration), p95(@duration) by bin(5m)

-- Track one request end-to-end
fields @timestamp, level, message, handler
| filter requestId = "YOUR-REQUEST-ID"
| sort @timestamp asc
```

### X-Ray distributed tracing

```
AWS Console → X-Ray → Traces → filter: service("task-manager-get-tasks-dev")
```

Shows full request breakdown:
- API Gateway latency
- Lambda Authorizer duration + blocklist check
- Main Lambda cold start vs warm start
- DynamoDB operation latency per call

---

## Security

| Layer | Control |
|---|---|
| API authentication | Cognito JWT — validated by Lambda Authorizer |
| Token revocation | DynamoDB blocklist — real logout via `POST /auth/logout` |
| Per-user authorization | Ownership checks — 404 on cross-user access (not 403) |
| Input sanitization | XSS stripped at DTO setter level before validation |
| CI/CD credentials | OIDC short-lived tokens — no long-lived AWS keys |
| Lambda IAM | Least privilege — `DynamoDBCrudPolicy` scoped to own tables |
| Cognito registration | `AdminCreateUserOnly` — no self-registration |
| Log retention | 14-day auto-delete via CloudWatch log group TTL |
| Rate limiting | 100 req/s sustained, burst 50 at API Gateway level |

---

## Load Test Results

Tested with k6 against live AWS infrastructure:

| Metric | Result |
|---|---|
| Peak concurrent users | 50 |
| Total requests | 12,685 |
| Requests per second | 52.57 |
| p50 latency | 81ms |
| p95 latency | 130ms |
| p99 latency | 3,436ms (Lambda cold starts) |
| Error rate | 0.00% |
| Test duration | 4 minutes |

Full CRUD cycle per virtual user: POST → GET → GET/{id} → PUT → DELETE.

---

## CI/CD

GitHub Actions pipeline — `.github/workflows/deploy.yml`:

```
Push to main
  ↓
Build & Test job
  → mvn clean package
  → mvn test (63 tests must pass)
  ↓
Deploy job (only if tests pass)
  → OIDC assumes GitHubActionsDeployRole
  → sam deploy --no-confirm-changeset
  → Prints live API URL
```

OIDC short-lived tokens scoped to this repo — no long-lived AWS access keys stored in GitHub Secrets.

---

## Resume Line

> *"Built a production-grade serverless Task Manager REST API using Java 17, Spring Boot 3, AWS Lambda, API Gateway, DynamoDB, and Cognito — sustained 50 concurrent users at p95 130ms with 0.00% error rate (k6 load tested). Implemented Lambda Authorizer for centralized JWT validation and token revocation, GSI-based queries (O(k) vs O(n) scan), cursor-based pagination, soft delete for audit trails, XSS input sanitization, AWS X-Ray distributed tracing, and per-endpoint Lambda functions with SnapStart for fault isolation and independent scaling. Infrastructure as code via AWS SAM, CI/CD via GitHub Actions with OIDC. 63 automated tests across unit and integration layers."*

---

## What I Learned

### AWS & Cloud
- **Lambda cold starts** — SnapStart snapshots initialized JVM state, reducing ~8s to ~1s
- **Lambda memory = CPU** — increasing MemorySize increases CPU proportionally
- **GSI limitations** — can only add one GSI at a time to existing DynamoDB tables; multiple GSIs require sequential updates or table recreation
- **DynamoDB TTL** — auto-deletes items after epoch timestamp — perfect for token blocklist cleanup
- **CloudFormation state sync** — CLI changes to resources create drift; stack must be reconciled before next deploy
- **DeletionPolicy: Retain** — protects data from stack deletion but causes EarlyValidation errors on redeploy if tables already exist
- **Lambda Authorizer caching** — authorizer results cached per token for 5 minutes, reducing redundant auth Lambda invocations
- **SnapStart + Lambda Version** — SnapStart requires a published `AWS::Lambda::Version` resource; without it SnapStart is configured but never activated
- **TOKEN vs REQUEST Authorizer** — TOKEN type receives token via `event.getAuthorizationToken()`; REQUEST type uses `event.getHeaders()` — use TOKEN when auth decision is based only on the JWT

### Spring Boot & Java
- **Dual repository pattern** — @Profile activates different implementations behind a single interface
- **Flat JAR vs BOOT-INF** — Spring Boot repackage incompatible with Lambda; maven-shade-plugin produces correct format
- **@PrePersist silently fails on DynamoDB** — timestamps moved to service layer for DB-agnostic behavior
- **Interface default methods** — avoid abstract super calls in Spring Data interfaces; use derived queries instead
- **DTO setter sanitization** — sanitizing in setters guarantees it always runs before @Valid, regardless of call site
- **DynamoDB String status** — storing status as String (not enum) required for GSI sort key compatibility

### Security
- **Lambda Authorizer vs Cognito Authorizer** — Cognito validates JWT only; Lambda Authorizer adds blocklist checks
- **JWT jti for revocation** — storing jti (short UUID) is more efficient than storing full 800-char token
- **TTL on blocklist** — matches token expiry so entries auto-delete when they'd be invalid anyway
- **404 vs 403 for ownership failures** — 403 confirms resource exists; 404 gives nothing away
- **OIDC in CI/CD** — short-lived tokens expire after pipeline run; long-lived keys need manual rotation
- **Authorizer cache and revocation** — with 300s cache TTL, revoked token remains valid for up to 5 minutes; reduce TTL for stricter security requirements

### Debugging Real Problems
- **Spring Boot 4 vs 3.2.5** — aws-serverless-java-container incompatible with Boot 4
- **BOOT-INF/classes** — Lambda ClassNotFoundException until maven-shade-plugin configured correctly
- **DynamoDB two-GSI limit** — "Cannot perform more than one GSI creation in a single update"
- **CloudFormation stack drift** — CLI GSI creation caused state mismatch requiring full delete and recreate
- **DeletionPolicy + redeploy** — retained tables cause EarlyValidation errors; delete tables before redeploying same stack
- **SnapStart EarlyValidation** — `AWS::Lambda::Version` on redeploy causes ResourceExistenceCheck failure; must remove version resource or delete stack completely
- **UPDATE_ROLLBACK_FAILED** — use `continue-update-rollback --resources-to-skip` to recover; delete changeset if stack stuck in REVIEW_IN_PROGRESS
- **TOKEN type Authorizer** — `event.getAuthorizationToken()` not `event.getHeaders()` — wrong extraction causes silent deny for all requests
- **Authorizer cache denial** — failed auth result cached for 300s; flush with `aws apigateway flush-stage-authorizers-cache` when debugging