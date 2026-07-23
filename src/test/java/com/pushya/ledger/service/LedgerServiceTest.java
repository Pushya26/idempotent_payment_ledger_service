package com.pushya.ledger.service;

import com.pushya.ledger.domain.LedgerEntry;
import com.pushya.ledger.domain.PaymentIntent;
import com.pushya.ledger.domain.PaymentIntentStatus;
import com.pushya.ledger.repository.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {
    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks
    private LedgerService ledgerService;

    @Test
    void recordsTwoEntriesForSuccessfulPayment() {
        PaymentIntent paymentIntent = new PaymentIntent(UUID.randomUUID(), 150_000L, "INR",
                PaymentIntentStatus.SUCCEEDED, Instant.now(), Instant.now());

        ledgerService.recordSuccess(paymentIntent);

        verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));
    }
}
