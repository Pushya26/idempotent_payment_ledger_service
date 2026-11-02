package com.pushya.ledger.api;

import com.pushya.ledger.domain.PaymentIntent;
import java.time.Instant;
import java.util.UUID;

public record PaymentIntentResponse(UUID id, long amountMinor, String currency, String status,
                                     Instant createdAt, Instant updatedAt) {
    public static PaymentIntentResponse from(PaymentIntent paymentIntent) {
        return new PaymentIntentResponse(
                paymentIntent.getId(),
                paymentIntent.getAmountMinor(),
                paymentIntent.getCurrency(),
                paymentIntent.getStatus().name(),
                paymentIntent.getCreatedAt(),
                paymentIntent.getUpdatedAt());
    }
}
