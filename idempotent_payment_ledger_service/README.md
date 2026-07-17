# Idempotent Payment Ledger Service

This project implements a small payment-ledger backend in Java 21 and Spring Boot 4.1 with:

- payment intent creation and confirmation
- double-entry ledger rows for successful payments
- idempotency enforcement via a servlet filter backed by Redis
- Flyway-managed Postgres schema

## Local development

1. Start Postgres and Redis with Docker Compose:
   ```bash
   docker compose up -d
   ```
2. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```

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
