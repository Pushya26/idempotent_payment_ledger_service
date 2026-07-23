package com.pushya.ledger.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreatePaymentIntentRequest(
        @Positive long amountMinor,
        @NotBlank @Size(min = 3, max = 3) String currency) {
}
