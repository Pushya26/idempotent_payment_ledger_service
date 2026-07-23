package com.pushya.ledger.service;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.pushya.ledger.domain.LedgerEntry;
import com.pushya.ledger.domain.LedgerEntryType;

class LedgerInvariantTest {
    @Test
    void doubleEntryLedgerRowsBalanceToZero() {
        List<LedgerEntry> entries = List.of(
                new LedgerEntry(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "customer:holding", LedgerEntryType.DEBIT, 150_000L, "INR", java.time.Instant.now()),
                new LedgerEntry(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "platform:settlement", LedgerEntryType.CREDIT, 150_000L, "INR", java.time.Instant.now())
        );

        long debits = entries.stream().filter(entry -> entry.getType() == LedgerEntryType.DEBIT).mapToLong(LedgerEntry::getAmountMinor).sum();
        long credits = entries.stream().filter(entry -> entry.getType() == LedgerEntryType.CREDIT).mapToLong(LedgerEntry::getAmountMinor).sum();

        assertThat(debits).isEqualTo(credits);
    }
}
