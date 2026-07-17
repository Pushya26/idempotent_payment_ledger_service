package com.pushya.ledger.api;

import com.pushya.ledger.domain.PaymentIntent;
import com.pushya.ledger.domain.PaymentIntentStatus;
import com.pushya.ledger.service.PaymentIntentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentIntentControllerTest {
    @Mock
    private PaymentIntentService paymentIntentService;

    @InjectMocks
    private PaymentIntentController paymentIntentController;

    @Test
    void createReturnsCreatedResponse() {
        PaymentIntent paymentIntent = new PaymentIntent(UUID.randomUUID(), 150_000L, "INR",
                PaymentIntentStatus.REQUIRES_CONFIRMATION, Instant.now(), Instant.now());
        when(paymentIntentService.create(150_000L, "INR")).thenReturn(paymentIntent);

        var response = paymentIntentController.create(new CreatePaymentIntentRequest(150_000L, "INR"));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().currency()).isEqualTo("INR");
    }

    @Test
    void getReturnsExistingPaymentIntent() {
        UUID id = UUID.randomUUID();
        PaymentIntent paymentIntent = new PaymentIntent(id, 150_000L, "INR",
                PaymentIntentStatus.REQUIRES_CONFIRMATION, Instant.now(), Instant.now());
        when(paymentIntentService.find(id)).thenReturn(Optional.of(paymentIntent));

        var response = paymentIntentController.get(id);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(id);
    }
}
