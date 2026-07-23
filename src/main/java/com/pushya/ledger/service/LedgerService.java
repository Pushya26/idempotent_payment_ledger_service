package com.pushya.ledger.service;

import com.pushya.ledger.domain.LedgerEntry;
import com.pushya.ledger.domain.LedgerEntryType;
import com.pushya.ledger.domain.PaymentIntent;
import com.pushya.ledger.repository.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.UUID;

@Service
public class LedgerService {
    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerService(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    public void recordSuccess(PaymentIntent paymentIntent) {
        Instant now = Instant.now();
        ledgerEntryRepository.save(new LedgerEntry(UUID.randomUUID(), paymentIntent.getId(), "customer:holding",
                LedgerEntryType.DEBIT, paymentIntent.getAmountMinor(), paymentIntent.getCurrency(), now));
        ledgerEntryRepository.save(new LedgerEntry(UUID.randomUUID(), paymentIntent.getId(), "platform:settlement",
                LedgerEntryType.CREDIT, paymentIntent.getAmountMinor(), paymentIntent.getCurrency(), now));
    }
}
