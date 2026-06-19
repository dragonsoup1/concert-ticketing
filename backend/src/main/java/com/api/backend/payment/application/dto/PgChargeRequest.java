package com.api.backend.payment.application.dto;

import java.math.BigDecimal;

public record PgChargeRequest(
    String idempotencyKey,
    BigDecimal amount,
    String description
) {}
