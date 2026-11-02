package com.pushya.ledger.api;

import com.pushya.ledger.domain.PaymentIntent;
import com.pushya.ledger.domain.PaymentIntentStatus;
import com.pushya.ledger.service.PaymentIntentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/payment_intents")
public class PaymentIntentController {
    private final PaymentIntentService paymentIntentService;

    public PaymentIntentController(PaymentIntentService paymentIntentService) {
        this.paymentIntentService = paymentIntentService;
    }

    @PostMapping
    public ResponseEntity<PaymentIntentResponse> create(@Valid @RequestBody CreatePaymentIntentRequest request) {
        PaymentIntent paymentIntent = paymentIntentService.create(request.amountMinor(), request.currency());
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentIntentResponse.from(paymentIntent));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<PaymentIntentResponse> confirm(@PathVariable UUID id) {
        return ResponseEntity.ok(PaymentIntentResponse.from(paymentIntentService.confirm(id)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<PaymentIntentResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(PaymentIntentResponse.from(paymentIntentService.cancel(id)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentIntentResponse> get(@PathVariable UUID id) {
        return paymentIntentService.find(id)
                .map(paymentIntent -> ResponseEntity.ok(PaymentIntentResponse.from(paymentIntent)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<Page<PaymentIntentResponse>> list(
            @RequestParam(required = false) PaymentIntentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PaymentIntentResponse> result = paymentIntentService.list(status, pageRequest)
                .map(PaymentIntentResponse::from);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/ledger")
    public ResponseEntity<List<LedgerEntryResponse>> getLedgerEntries(@PathVariable UUID id) {
        List<LedgerEntryResponse> entries = paymentIntentService.getLedgerEntries(id).stream()
                .map(LedgerEntryResponse::from)
                .toList();
        return ResponseEntity.ok(entries);
    }
}
