package com.pushya.ledger.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pushya.ledger.domain.PaymentIntent;
import com.pushya.ledger.domain.PaymentIntentStatus;
import com.pushya.ledger.domain.IllegalStateTransitionException;
import com.pushya.ledger.observability.PaymentMetrics;
import com.pushya.ledger.repository.PaymentIntentRepository;

@ExtendWith(MockitoExtension.class)
class PaymentIntentServiceTest {
    @Mock
    private PaymentIntentRepository paymentIntentRepository;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private PaymentMetrics metrics;

    @Mock
    private Timer confirmTimer;

    @InjectMocks
    private PaymentIntentService paymentIntentService;

    @Test
    void confirmMarksSuccessfulIntentAsSucceededAndPersistsState() {
        UUID id = UUID.randomUUID();
        PaymentIntent paymentIntent = new PaymentIntent(id, 150_000L, "INR",
                PaymentIntentStatus.REQUIRES_CONFIRMATION, Instant.now(), Instant.now());
        when(paymentIntentRepository.findById(id)).thenReturn(Optional.of(paymentIntent));
        when(paymentIntentRepository.save(paymentIntent)).thenReturn(paymentIntent);
        when(metrics.getConfirmTimer()).thenReturn(confirmTimer);
        when(confirmTimer.record(any(java.util.function.Supplier.class))).thenAnswer(inv -> {
            java.util.function.Supplier<?> supplier = inv.getArgument(0);
            return supplier.get();
        });

        PaymentIntent result = paymentIntentService.confirm(id);

        assertThat(result.getStatus()).isEqualTo(PaymentIntentStatus.SUCCEEDED);
        verify(ledgerService).recordSuccess(paymentIntent);
        verify(paymentIntentRepository).save(paymentIntent);
        verify(metrics).incrementConfirmSuccess();
    }

    @Test
    void confirmMarksDeclinedIntentAsFailedWithoutLedgerEntries() {
        UUID id = UUID.randomUUID();
        PaymentIntent paymentIntent = new PaymentIntent(id, 0L, "INR",
                PaymentIntentStatus.REQUIRES_CONFIRMATION, Instant.now(), Instant.now());
        when(paymentIntentRepository.findById(id)).thenReturn(Optional.of(paymentIntent));
        when(paymentIntentRepository.save(paymentIntent)).thenReturn(paymentIntent);
        when(metrics.getConfirmTimer()).thenReturn(confirmTimer);
        when(confirmTimer.record(any(java.util.function.Supplier.class))).thenAnswer(inv -> {
            java.util.function.Supplier<?> supplier = inv.getArgument(0);
            return supplier.get();
        });

        PaymentIntent result = paymentIntentService.confirm(id);

        assertThat(result.getStatus()).isEqualTo(PaymentIntentStatus.FAILED);
        verify(paymentIntentRepository).save(paymentIntent);
        verify(ledgerService, never()).recordSuccess(any());
        verify(metrics).incrementConfirmFailure();
    }

    @Test
    void cancelTransitionsFromRequiresConfirmationToCancelled() {
        UUID id = UUID.randomUUID();
        PaymentIntent paymentIntent = new PaymentIntent(id, 150_000L, "INR",
                PaymentIntentStatus.REQUIRES_CONFIRMATION, Instant.now(), Instant.now());
        when(paymentIntentRepository.findById(id)).thenReturn(Optional.of(paymentIntent));
        when(paymentIntentRepository.save(paymentIntent)).thenReturn(paymentIntent);

        PaymentIntent result = paymentIntentService.cancel(id);

        assertThat(result.getStatus()).isEqualTo(PaymentIntentStatus.CANCELLED);
        verify(paymentIntentRepository).save(paymentIntent);
        verify(metrics).incrementCancelled();
    }

    @Test
    void cancelRejectsAlreadySucceededIntent() {
        UUID id = UUID.randomUUID();
        PaymentIntent paymentIntent = new PaymentIntent(id, 150_000L, "INR",
                PaymentIntentStatus.SUCCEEDED, Instant.now(), Instant.now());
        when(paymentIntentRepository.findById(id)).thenReturn(Optional.of(paymentIntent));

        assertThatThrownBy(() -> paymentIntentService.cancel(id))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void createIncrementMetric() {
        when(paymentIntentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        paymentIntentService.create(100_00L, "USD");

        verify(metrics).incrementCreated();
    }
}
