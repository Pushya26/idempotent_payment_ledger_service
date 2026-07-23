# Idempotent Payment Ledger Service

This project implements a small payment-ledger backend in Java 21 and Spring Boot 4.1 with:

- payment intent creation and confirmation
- double-entry ledger rows for successful payments
- idempotency enforcement via a servlet filter backed by Redis
- Flyway-managed Postgres schema

## Local development

### Running unit tests

Unit tests do not require any external services:

```bash
./mvnw clean test
```

### Running integration tests

Integration tests require Docker Compose services to be running. There are two ways to run them:

**Option 1: Using the convenience script (recommended)**

Linux/macOS:

```bash
bash run-integration-tests.sh
```

Windows:

```bash
run-integration-tests.bat
```

This script will:

- Start Docker Compose services
- Wait for services to be ready
- Run integration tests with the `integration` Maven profile
- Optionally stop services when complete

**Option 2: Manual steps**

1. Start Docker Compose services:

   ```bash
   docker compose up -d
   ```

2. Run integration tests:

   ```bash
   ./mvnw verify -Pintegration
   ```

3. Stop services when done:
   ```bash
   docker compose down
   ```

### Running the application with services

```bash
docker compose up -d
./mvnw spring-boot:run
```

Services will be automatically detected via Spring Boot Docker Compose support.

## API example

Create a payment intent:

```bash
curl -X POST http://localhost:8080/v1/payment_intents \
  -H 'Content-Type: application/json' \
  -d '{"amountMinor":150000,"currency":"INR"}'
```

Confirm it:

```bash
curl -X POST http://localhost:8080/v1/payment_intents/{id}/confirm \
  -H 'Idempotency-Key: demo-key'
```

## Idempotency behavior

- The Idempotency-Key header is required for confirm requests.
- Reusing the same key with the same body replays the original result.
- Reusing a key with a different body returns 422.
- Concurrent requests for the same key receive a retryable 409 while only one settles the payment.
