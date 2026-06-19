package com.api.backend.payment.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PaymentRequest(
    @NotNull Long reservationId,
    @NotNull @Positive BigDecimal amount
) {}
