package com.pushya.ledger.repository;

import com.pushya.ledger.domain.PaymentIntent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, UUID> {
}
