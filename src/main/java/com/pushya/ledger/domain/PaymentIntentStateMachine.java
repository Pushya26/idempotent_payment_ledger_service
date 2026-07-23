package com.pushya.ledger.domain;

import java.util.Map;
import java.util.Set;

public final class PaymentIntentStateMachine {
    private static final Map<PaymentIntentStatus, Set<PaymentIntentStatus>> ALLOWED = Map.of(
            PaymentIntentStatus.REQUIRES_CONFIRMATION, Set.of(PaymentIntentStatus.PROCESSING),
            PaymentIntentStatus.PROCESSING, Set.of(PaymentIntentStatus.SUCCEEDED, PaymentIntentStatus.FAILED),
            PaymentIntentStatus.SUCCEEDED, Set.of(),
            PaymentIntentStatus.FAILED, Set.of()
    );

    private PaymentIntentStateMachine() {
    }

    public static void assertTransition(PaymentIntentStatus from, PaymentIntentStatus to) {
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new IllegalStateTransitionException(from, to);
        }
    }
}
