package com.pushya.ledger.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentIntentStateMachineTest {
    @Test
    void allowsExpectedTransitions() {
        assertThatCode(() -> PaymentIntentStateMachine.assertTransition(PaymentIntentStatus.REQUIRES_CONFIRMATION, PaymentIntentStatus.PROCESSING))
                .doesNotThrowAnyException();
        assertThatCode(() -> PaymentIntentStateMachine.assertTransition(PaymentIntentStatus.PROCESSING, PaymentIntentStatus.SUCCEEDED))
                .doesNotThrowAnyException();
        assertThatCode(() -> PaymentIntentStateMachine.assertTransition(PaymentIntentStatus.PROCESSING, PaymentIntentStatus.FAILED))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsUnexpectedTransitions() {
        assertThatThrownBy(() -> PaymentIntentStateMachine.assertTransition(PaymentIntentStatus.REQUIRES_CONFIRMATION, PaymentIntentStatus.SUCCEEDED))
                .isInstanceOf(IllegalStateTransitionException.class);
        assertThatThrownBy(() -> PaymentIntentStateMachine.assertTransition(PaymentIntentStatus.SUCCEEDED, PaymentIntentStatus.FAILED))
                .isInstanceOf(IllegalStateTransitionException.class);
    }
}
