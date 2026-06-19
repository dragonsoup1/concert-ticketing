package com.api.backend.payment.api.dto;

import com.api.backend.payment.domain.Payment;
import com.api.backend.payment.domain.PaymentStatus;

import java.math.BigDecimal;

public record PaymentResponse(
    Long id,
    Long reservationId,
    BigDecimal amount,
    PaymentStatus status,
    String pgTransactionId
) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
            p.getId(),
            p.getReservation().getId(),
            p.getAmount(),
            p.getStatus(),
            p.getPgTransactionId()
        );
    }
}
