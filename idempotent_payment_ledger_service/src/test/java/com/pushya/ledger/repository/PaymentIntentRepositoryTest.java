package com.pushya.ledger.repository;

import com.pushya.ledger.domain.PaymentIntent;
import com.pushya.ledger.domain.PaymentIntentStatus;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentIntentRepositoryTest {
    @Test
    void createsPaymentIntentEntity() {
        PaymentIntent paymentIntent = new PaymentIntent(UUID.randomUUID(), 1000L, "INR",
                PaymentIntentStatus.REQUIRES_CONFIRMATION, Instant.now(), Instant.now());

        assertThat(paymentIntent.getId()).isNotNull();
        assertThat(paymentIntent.getStatus()).isEqualTo(PaymentIntentStatus.REQUIRES_CONFIRMATION);
    }
}
