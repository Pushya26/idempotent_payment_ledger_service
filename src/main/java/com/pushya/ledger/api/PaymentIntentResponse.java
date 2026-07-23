package com.pushya.ledger.api;

import com.pushya.ledger.domain.PaymentIntent;
import java.util.UUID;

public record PaymentIntentResponse(UUID id, long amountMinor, String currency, String status) {
    public static PaymentIntentResponse from(PaymentIntent paymentIntent) {
        return new PaymentIntentResponse(paymentIntent.getId(), paymentIntent.getAmountMinor(), paymentIntent.getCurrency(), paymentIntent.getStatus().name());
    }
}
