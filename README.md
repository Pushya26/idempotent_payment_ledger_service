# Idempotent Payment Ledger Service

A production-grade payment ledger microservice built with **Java 21** and **Spring Boot 4.1** that demonstrates idempotent payment processing with double-entry bookkeeping.

## Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                       Spring Boot Application                     │
│                                                                    │
│  ┌──────────────┐   ┌──────────────────┐   ┌──────────────────┐  │
│  │ Correlation   │──▶│  Idempotency     │──▶│  Payment Intent  │  │
│  │ ID Filter     │   │  Filter (Redis)  │   │  Controller      │  │
│  │ (Order 0)     │   │  (Order 1)       │   │  (REST API)      │  │
│  └──────────────┘   └──────────────────┘   └────────┬─────────┘  │
│                                                       │            │
│                           ┌───────────────────────────┘            │
│                           ▼                                        │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │                PaymentIntentService                           │ │
│  │  create() ──▶ REQUIRES_CONFIRMATION                          │ │
│  │  confirm() ─▶ PROCESSING ──▶ SUCCEEDED / FAILED             │ │
│  │  cancel() ──▶ CANCELLED                                      │ │
│  └────────┬─────────────────────────────────────────────────────┘ │
│           │                                                        │
│           ▼                                                        │
│  ┌──────────────────┐    ┌─────────────────────────────────────┐ │
│  │  LedgerService    │    │  Micrometer Metrics + Prometheus    │ │
│  │  (Double-Entry)   │    │  payment_intents.created            │ │
│  │  DEBIT + CREDIT   │    │  payment_intents.confirmed          │ │
│  └────────┬──────────┘    │  payment_intents.confirm.duration   │ │
│           │               └─────────────────────────────────────┘ │
└───────────┼──────────────────────────────────────────────────────┘
            │
   ┌────────┴────────┐         ┌──────────────┐
   │   PostgreSQL    │         │    Redis      │
   │   (Flyway)      │         │ (Idempotency) │
   └─────────────────┘         └──────────────┘
```

## Key Features

- **Idempotent payment confirmation** — Redis-backed SETNX claim/result pattern ensures exactly-once settlement even under 50+ concurrent requests
- **Double-entry ledger** — Every successful payment creates balanced DEBIT + CREDIT entries
- **State machine enforcement** — PaymentIntent transitions are validated at the domain level
- **Optimistic locking** — `@Version` column prevents lost updates under concurrency
- **RFC 7807 error responses** — All errors return standard `ProblemDetail` JSON
- **Structured JSON logging** — Logback JSON encoder in prod profile for ELK/Datadog
- **Correlation ID propagation** — `X-Correlation-Id` header threaded through MDC into every log line
- **Prometheus metrics** — Custom counters and timers for payment lifecycle events
- **Production Dockerfile** — Multi-stage build with ZGC, non-root user, health check

## Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Java 21 (Virtual Threads ready) |
| Framework | Spring Boot 4.1 |
| Database | PostgreSQL 16 (Flyway migrations) |
| Cache | Redis 7 (idempotency store) |
| Build | Maven, multi-stage Docker |
| Observability | Micrometer + Prometheus, structured logging |
| Testing | JUnit 5, Mockito, AssertJ, Testcontainers |

## API Endpoints

| Method | Path | Description | Idempotency-Key |
|--------|------|-------------|:---------------:|
| `POST` | `/v1/payment_intents` | Create a new payment intent | Not required |
| `POST` | `/v1/payment_intents/{id}/confirm` | Confirm and settle a payment | **Required** |
| `POST` | `/v1/payment_intents/{id}/cancel` | Cancel a pending payment | **Required** |
| `GET` | `/v1/payment_intents/{id}` | Get a payment intent by ID | — |
| `GET` | `/v1/payment_intents?status=SUCCEEDED&page=0&size=20` | List payment intents with pagination | — |

### Actuator Endpoints

| Path | Description |
|------|-------------|
| `/actuator/health` | Health check (DB + Redis indicators) |
| `/actuator/health/liveness` | Kubernetes liveness probe |
| `/actuator/health/readiness` | Kubernetes readiness probe |
| `/actuator/prometheus` | Prometheus metrics scrape endpoint |

## Local Development

### Prerequisites

- Java 21+
- Docker & Docker Compose

### Running Unit Tests

Unit tests do not require any external services:

```bash
./mvnw clean test
```

### Running Integration Tests

Integration tests require Docker Compose services to be running:

**Option 1: Using the convenience script (recommended)**

Linux/macOS:
```bash
bash run-integration-tests.sh
```

Windows:
```bash
run-integration-tests.bat
```

**Option 2: Manual steps**

```bash
docker compose up -d
./mvnw verify -Pintegration
docker compose down
```

### Running the Application

```bash
docker compose up -d
./mvnw spring-boot:run
```

## API Examples

**Create a payment intent:**

```bash
curl -s -X POST http://localhost:8080/v1/payment_intents \
  -H 'Content-Type: application/json' \
  -d '{"amountMinor":150000,"currency":"INR"}' | jq
```

**Confirm it (idempotent):**

```bash
curl -s -X POST http://localhost:8080/v1/payment_intents/{id}/confirm \
  -H 'Idempotency-Key: unique-key-123' | jq
```

**Cancel a pending payment:**

```bash
curl -s -X POST http://localhost:8080/v1/payment_intents/{id}/cancel \
  -H 'Idempotency-Key: cancel-key-456' | jq
```

**List payment intents:**

```bash
curl -s 'http://localhost:8080/v1/payment_intents?status=SUCCEEDED&page=0&size=10' | jq
```

## Idempotency Behavior

- The `Idempotency-Key` header is required for `confirm` and `cancel` requests
- Creating a payment intent does **not** require an idempotency key
- Reusing the same key with the same body replays the original result (cache hit)
- Reusing a key with a different body returns `422 Unprocessable Entity`
- Concurrent requests for the same key receive a retryable `409 Conflict` while only one settles

## Production Deployment

**Using the production Docker Compose:**

```bash
DB_PASSWORD=secure_password docker compose -f docker-compose.prod.yml up -d
```

**Environment variables for `application-prod.yml`:**

| Variable | Description | Default |
|----------|-------------|---------|
| `DATABASE_URL` | JDBC connection string | — |
| `DATABASE_USERNAME` | DB username | — |
| `DATABASE_PASSWORD` | DB password | — |
| `REDIS_HOST` | Redis hostname | — |
| `REDIS_PORT` | Redis port | `6379` |
| `REDIS_PASSWORD` | Redis password | (empty) |
| `PORT` | Application port | `8080` |

## Frontend Dashboard

A built-in payment dashboard SPA is served at the application root:

```
http://localhost:8080/
```

**Features:**
- **Live status** — API health indicator updates every 15 seconds
- **Stats overview** — Real-time counters for each payment status
- **Create payments** — Amount + currency form with instant feedback
- **Filter & paginate** — Filter chips for each status, paginated table
- **Confirm / Cancel** — One-click actions directly from the table or detail modal
- **Detail modal** — Full payment details with timestamps and ID
- **Toast notifications** — Success/error feedback on every action
- **Auto-refresh** — Table refreshes every 30 seconds
- **Responsive** — Works on mobile and desktop

No separate build step required. The dashboard is a self-contained HTML file served as a Spring Boot static resource.

## Project Structure

```
src/main/
├── java/com/pushya/ledger/
│   ├── api/                          # REST controllers, DTOs, exception handler
│   │   ├── PaymentIntentController   # CRUD + confirm/cancel endpoints
│   │   ├── GlobalExceptionHandler    # RFC 7807 ProblemDetail responses
│   │   ├── CreatePaymentIntentRequest
│   │   ├── PaymentIntentResponse
│   │   └── LedgerEntryResponse
│   ├── domain/                       # Core domain model
│   │   ├── PaymentIntent             # JPA entity with @Version
│   │   ├── PaymentIntentStatus       # State enum
│   │   ├── PaymentIntentStateMachine # Transition validation
│   │   ├── LedgerEntry               # Double-entry ledger row
│   │   └── LedgerEntryType           # DEBIT / CREDIT
│   ├── idempotency/
│   │   └── IdempotencyFilter         # Redis SETNX-based servlet filter
│   ├── observability/
│   │   ├── CorrelationIdFilter       # X-Correlation-Id MDC propagation
│   │   └── PaymentMetrics            # Custom Micrometer counters & timers
│   ├── service/
│   │   ├── PaymentIntentService      # Business logic + metrics
│   │   └── LedgerService             # Double-entry recording
│   ├── repository/
│   │   ├── PaymentIntentRepository
│   │   └── LedgerEntryRepository
│   └── config/
│       ├── RedisConfig
│       └── WebConfig                 # CORS configuration
└── resources/
    ├── static/
    │   └── index.html                # Payment dashboard SPA
    ├── db/migration/
    │   ├── V1__create_payment_intent_and_ledger_tables.sql
    │   └── V2__add_version_and_indexes.sql
    ├── application.yml
    ├── application-prod.yml
    └── logback-spring.xml
```

