package com.api.backend.payment.application;

import com.api.backend.payment.application.dto.PgChargeRequest;
import com.api.backend.payment.application.dto.PgResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class PaymentGatewayClient {

    /**
     * 실제 PG 연동 자리. Circuit Breaker가 열리면 fallback으로 PENDING 처리.
     * - slidingWindowSize=10, failureRateThreshold=50%, waitDuration=10s
     */
    @CircuitBreaker(name = "paymentGateway", fallbackMethod = "chargeFallback")
    @Retry(name = "paymentGateway")
    public PgResponse charge(PgChargeRequest request) {
        log.info("PG 결제 요청: idempotencyKey={}, amount={}", request.idempotencyKey(), request.amount());
        // TODO: 실제 PG HTTP 클라이언트 연동
        return PgResponse.success(UUID.randomUUID().toString());
    }

    private PgResponse chargeFallback(PgChargeRequest request, Exception ex) {
        log.warn("PG Circuit Breaker 발동 — PENDING 처리: key={}, reason={}",
            request.idempotencyKey(), ex.getMessage());
        return PgResponse.pendingFallback();
    }
}
