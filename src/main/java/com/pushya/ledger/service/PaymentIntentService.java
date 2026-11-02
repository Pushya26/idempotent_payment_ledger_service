package com.pushya.ledger.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pushya.ledger.domain.LedgerEntry;
import com.pushya.ledger.domain.PaymentIntent;
import com.pushya.ledger.domain.PaymentIntentStateMachine;
import com.pushya.ledger.domain.PaymentIntentStatus;
import com.pushya.ledger.observability.PaymentMetrics;
import com.pushya.ledger.repository.PaymentIntentRepository;

@Service
public class PaymentIntentService {
    private final PaymentIntentRepository paymentIntentRepository;
    private final LedgerService ledgerService;
    private final PaymentMetrics metrics;

    public PaymentIntentService(PaymentIntentRepository paymentIntentRepository,
                                LedgerService ledgerService,
                                PaymentMetrics metrics) {
        this.paymentIntentRepository = paymentIntentRepository;
        this.ledgerService = ledgerService;
        this.metrics = metrics;
    }

    @Transactional
    public PaymentIntent create(long amountMinor, String currency) {
        PaymentIntent paymentIntent = new PaymentIntent(UUID.randomUUID(), amountMinor, currency,
                PaymentIntentStatus.REQUIRES_CONFIRMATION, Instant.now(), Instant.now());
        PaymentIntent saved = paymentIntentRepository.save(paymentIntent);
        metrics.incrementCreated();
        return saved;
    }

    @Transactional
    public PaymentIntent confirm(UUID id) {
        return metrics.getConfirmTimer().record(() -> {
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
                metrics.incrementConfirmSuccess();
            } else {
                metrics.incrementConfirmFailure();
            }
            return paymentIntentRepository.save(paymentIntent);
        });
    }

    @Transactional
    public PaymentIntent cancel(UUID id) {
        PaymentIntent paymentIntent = paymentIntentRepository.findById(id)
                .orElseThrow(() -> new PaymentIntentNotFoundException(id));

        PaymentIntentStateMachine.assertTransition(paymentIntent.getStatus(), PaymentIntentStatus.CANCELLED);
        paymentIntent.setStatus(PaymentIntentStatus.CANCELLED);
        paymentIntent.setUpdatedAt(Instant.now());
        PaymentIntent saved = paymentIntentRepository.save(paymentIntent);
        metrics.incrementCancelled();
        return saved;
    }

    public Optional<PaymentIntent> find(UUID id) {
        return paymentIntentRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Page<PaymentIntent> list(PaymentIntentStatus status, Pageable pageable) {
        if (status != null) {
            return paymentIntentRepository.findByStatus(status, pageable);
        }
        return paymentIntentRepository.findAll(pageable);
    }

    public List<LedgerEntry> getLedgerEntries(UUID id) {
        paymentIntentRepository.findById(id)
                .orElseThrow(() -> new PaymentIntentNotFoundException(id));
        return ledgerService.findByPaymentIntentId(id);
    }

    private boolean simulateSettlement(PaymentIntent paymentIntent) {
        return paymentIntent.getAmountMinor() != 0;
    }
}
