package com.pushya.ledger.domain;

public class IllegalStateTransitionException extends RuntimeException {
    public IllegalStateTransitionException(PaymentIntentStatus from, PaymentIntentStatus to) {
        super("Illegal state transition from %s to %s".formatted(from, to));
    }
}
