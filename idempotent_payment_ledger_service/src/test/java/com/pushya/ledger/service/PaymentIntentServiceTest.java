package com.pushya.ledger.service;

import com.pushya.ledger.domain.PaymentIntent;
import com.pushya.ledger.domain.PaymentIntentStatus;
import com.pushya.ledger.repository.PaymentIntentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentIntentServiceTest {
    @Mock
    private PaymentIntentRepository paymentIntentRepository;

    @Mock
    private LedgerService ledgerService;

    @InjectMocks
    private PaymentIntentService paymentIntentService;

    @Test
    void confirmMarksSuccessfulIntentAsSucceededAndPersistsState() {
        UUID id = UUID.randomUUID();
        PaymentIntent paymentIntent = new PaymentIntent(id, 150_000L, "INR",
                PaymentIntentStatus.REQUIRES_CONFIRMATION, Instant.now(), Instant.now());
        when(paymentIntentRepository.findById(id)).thenReturn(Optional.of(paymentIntent));
        when(paymentIntentRepository.save(paymentIntent)).thenReturn(paymentIntent);

        PaymentIntent result = paymentIntentService.confirm(id);

        assertThat(result.getStatus()).isEqualTo(PaymentIntentStatus.SUCCEEDED);
        verify(ledgerService).recordSuccess(paymentIntent);
        verify(paymentIntentRepository).save(paymentIntent);
    }

    @Test
    void confirmMarksDeclinedIntentAsFailedWithoutLedgerEntries() {
        UUID id = UUID.randomUUID();
        PaymentIntent paymentIntent = new PaymentIntent(id, 0L, "INR",
                PaymentIntentStatus.REQUIRES_CONFIRMATION, Instant.now(), Instant.now());
        when(paymentIntentRepository.findById(id)).thenReturn(Optional.of(paymentIntent));
        when(paymentIntentRepository.save(paymentIntent)).thenReturn(paymentIntent);

        PaymentIntent result = paymentIntentService.confirm(id);

        assertThat(result.getStatus()).isEqualTo(PaymentIntentStatus.FAILED);
        verify(paymentIntentRepository).save(paymentIntent);
    }
}
