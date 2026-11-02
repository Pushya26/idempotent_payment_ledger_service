package com.pushya.ledger.api;

import com.pushya.ledger.domain.LedgerEntry;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryResponse(UUID id, UUID paymentIntentId, String account, String type,
                                   long amountMinor, String currency, Instant createdAt) {
    public static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(entry.getId(), entry.getPaymentIntentId(), entry.getAccount(),
                entry.getType().name(), entry.getAmountMinor(), entry.getCurrency(), entry.getCreatedAt());
    }
}
