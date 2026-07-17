package com.pushya.ledger.service;

import com.pushya.ledger.domain.PaymentIntent;
import com.pushya.ledger.domain.PaymentIntentStateMachine;
import com.pushya.ledger.domain.PaymentIntentStatus;
import com.pushya.ledger.repository.PaymentIntentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentIntentService {
    private final PaymentIntentRepository paymentIntentRepository;
    private final LedgerService ledgerService;

    public PaymentIntentService(PaymentIntentRepository paymentIntentRepository, LedgerService ledgerService) {
        this.paymentIntentRepository = paymentIntentRepository;
        this.ledgerService = ledgerService;
    }

    @Transactional
    public PaymentIntent create(long amountMinor, String currency) {
        PaymentIntent paymentIntent = new PaymentIntent(UUID.randomUUID(), amountMinor, currency,
                PaymentIntentStatus.REQUIRES_CONFIRMATION, Instant.now(), Instant.now());
        return paymentIntentRepository.save(paymentIntent);
    }

    @Transactional
    public PaymentIntent confirm(UUID id) {
        PaymentIntent paymentIntent = paymentIntentRepository.findById(id)
                .orElseThrow(() -> new PaymentIntentNotFoundException(id));

        PaymentIntentStateMachine.assertTransition(paymentIntent.getStatus(), PaymentIntentStatus.PROCESSING);
        paymentIntent.setStatus(PaymentIntentStatus.PROCESSING);
        paymentIntent.setUpdatedAt(Instant.now());

        boolean approved = simulateSettlement(paymentIntent);
        PaymentIntentStatus finalStatus = approved ? PaymentIntentStatus.SUCCEEDED : PaymentIntentStatus.FAILED;
        PaymentIntentStateMachine.assertTransition(paymentIntent.getStatus(), finalStatus);
        paymentIntent.setStatus(finalStatus);
        paymentIntent.setUpdatedAt(Instant.now());

        if (approved) {
            ledgerService.recordSuccess(paymentIntent);
        }
        return paymentIntent;
    }

    public Optional<PaymentIntent> find(UUID id) {
        return paymentIntentRepository.findById(id);
    }

    private boolean simulateSettlement(PaymentIntent paymentIntent) {
        return paymentIntent.getAmountMinor() != 0;
    }
}
