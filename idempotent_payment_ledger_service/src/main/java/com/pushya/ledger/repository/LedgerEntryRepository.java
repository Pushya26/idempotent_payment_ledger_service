package com.pushya.ledger.repository;

import com.pushya.ledger.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findByPaymentIntentId(UUID paymentIntentId);
}
