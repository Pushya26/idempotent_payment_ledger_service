package com.pushya.ledger.service;

import java.util.UUID;

public class PaymentIntentNotFoundException extends RuntimeException {
    public PaymentIntentNotFoundException(UUID id) {
        super("Payment intent %s was not found".formatted(id));
    }
}
