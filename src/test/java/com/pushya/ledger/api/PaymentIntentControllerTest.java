package com.pushya.ledger.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.pushya.ledger.domain.PaymentIntent;
import com.pushya.ledger.domain.PaymentIntentStatus;
import com.pushya.ledger.service.PaymentIntentService;

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
        assertThat(response.getBody().createdAt()).isNotNull();
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

    @Test
    void getReturnsNotFoundForMissingIntent() {
        UUID id = UUID.randomUUID();
        when(paymentIntentService.find(id)).thenReturn(Optional.empty());

        var response = paymentIntentController.get(id);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void confirmReturnsOkWithUpdatedStatus() {
        UUID id = UUID.randomUUID();
        PaymentIntent paymentIntent = new PaymentIntent(id, 150_000L, "INR",
                PaymentIntentStatus.SUCCEEDED, Instant.now(), Instant.now());
        when(paymentIntentService.confirm(id)).thenReturn(paymentIntent);

        var response = paymentIntentController.confirm(id);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("SUCCEEDED");
    }

    @Test
    void cancelReturnsOkWithCancelledStatus() {
        UUID id = UUID.randomUUID();
        PaymentIntent paymentIntent = new PaymentIntent(id, 150_000L, "INR",
                PaymentIntentStatus.CANCELLED, Instant.now(), Instant.now());
        when(paymentIntentService.cancel(id)).thenReturn(paymentIntent);

        var response = paymentIntentController.cancel(id);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("CANCELLED");
    }

    @Test
    void listReturnsPaginatedResults() {
        PaymentIntent pi1 = new PaymentIntent(UUID.randomUUID(), 100_00L, "USD",
                PaymentIntentStatus.REQUIRES_CONFIRMATION, Instant.now(), Instant.now());
        PaymentIntent pi2 = new PaymentIntent(UUID.randomUUID(), 200_00L, "USD",
                PaymentIntentStatus.SUCCEEDED, Instant.now(), Instant.now());
        Page<PaymentIntent> page = new PageImpl<>(List.of(pi1, pi2));
        when(paymentIntentService.list(eq(null), any(PageRequest.class))).thenReturn(page);

        var response = paymentIntentController.list(null, 0, 20);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(2);
    }
}
