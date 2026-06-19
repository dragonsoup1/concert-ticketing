package com.api.backend.payment.application;

import com.api.backend.payment.domain.Payment;
import lombok.Getter;

@Getter
public class AlreadyProcessedPaymentException extends RuntimeException {

    private final Payment payment;

    public AlreadyProcessedPaymentException(Payment payment) {
        super("이미 처리된 결제입니다. idempotencyKey=" + payment.getIdempotencyKey());
        this.payment = payment;
    }
}
