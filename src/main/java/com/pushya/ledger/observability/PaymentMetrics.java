package com.pushya.ledger.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Centralized custom metrics for the payment lifecycle.
 * Spring Boot auto-configures HTTP request metrics and JVM metrics;
 * this class adds domain-specific counters and timers.
 */
@Component
public class PaymentMetrics {
    private final Counter createCounter;
    private final Counter confirmSuccessCounter;
    private final Counter confirmFailureCounter;
    private final Counter cancelCounter;
    private final Counter idempotencyHitCounter;
    private final Counter idempotencyConflictCounter;
    private final Timer confirmTimer;

    public PaymentMetrics(MeterRegistry registry) {
        this.createCounter = Counter.builder("payment_intents.created")
                .description("Number of payment intents created")
                .register(registry);

        this.confirmSuccessCounter = Counter.builder("payment_intents.confirmed")
                .tag("outcome", "success")
                .description("Number of successful confirmations")
                .register(registry);

        this.confirmFailureCounter = Counter.builder("payment_intents.confirmed")
                .tag("outcome", "failure")
                .description("Number of failed confirmations (settlement declined)")
                .register(registry);

        this.cancelCounter = Counter.builder("payment_intents.cancelled")
                .description("Number of cancelled payment intents")
                .register(registry);

        this.idempotencyHitCounter = Counter.builder("idempotency.cache_hit")
                .description("Number of idempotent replay responses served from cache")
                .register(registry);

        this.idempotencyConflictCounter = Counter.builder("idempotency.conflict")
                .description("Number of idempotency conflicts (concurrent or reuse)")
                .register(registry);

        this.confirmTimer = Timer.builder("payment_intents.confirm.duration")
                .description("Time taken to confirm a payment intent")
                .register(registry);
    }

    public void incrementCreated() {
        createCounter.increment();
    }

    public void incrementConfirmSuccess() {
        confirmSuccessCounter.increment();
    }

    public void incrementConfirmFailure() {
        confirmFailureCounter.increment();
    }

    public void incrementCancelled() {
        cancelCounter.increment();
    }

    public void incrementIdempotencyHit() {
        idempotencyHitCounter.increment();
    }

    public void incrementIdempotencyConflict() {
        idempotencyConflictCounter.increment();
    }

    public Timer getConfirmTimer() {
        return confirmTimer;
    }
}
