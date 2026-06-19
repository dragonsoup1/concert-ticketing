package com.api.backend.payment.application;

import com.api.backend.global.exception.EntityNotFoundException;
import com.api.backend.outbox.application.OutboxWriter;
import com.api.backend.payment.api.dto.PaymentRequest;
import com.api.backend.payment.api.dto.PaymentResponse;
import com.api.backend.payment.application.dto.PgChargeRequest;
import com.api.backend.payment.application.dto.PgResponse;
import com.api.backend.payment.domain.Payment;
import com.api.backend.payment.domain.PaymentRepository;
import com.api.backend.payment.domain.PaymentStatus;
import com.api.backend.reservation.application.ReservationService;
import com.api.backend.reservation.domain.Reservation;
import com.api.backend.reservation.domain.ReservationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;
    private final PaymentGatewayClient paymentGatewayClient;
    private final OutboxWriter outboxWriter;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String IDEMPOTENCY_PREFIX = "idempotency:";
    private static final Duration IDEMPOTENCY_TTL  = Duration.ofHours(24);

    /**
     * 결제 처리:
     * 1. DB unique key로 중복 결제 차단 (Redis 콜드스타트 fallback)
     * 2. PG 호출 (Circuit Breaker / Retry 적용)
     * 3. 같은 트랜잭션 안에서 Outbox 이벤트 INSERT → at-least-once 발행 보장
     */
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request, String idempotencyKey) {
        // DB 중복 결제 방지 (멱등성 키 unique 제약)
        paymentRepository.findByIdempotencyKey(idempotencyKey).ifPresent(existing -> {
            log.info("DB 멱등성 캐시 히트: key={}", idempotencyKey);
            throw new AlreadyProcessedPaymentException(existing);
        });

        Reservation reservation = reservationRepository.findByIdWithDetails(request.reservationId())
            .orElseThrow(() -> new EntityNotFoundException("예약을 찾을 수 없습니다."));

        PgResponse pgResponse = paymentGatewayClient.charge(
            new PgChargeRequest(idempotencyKey, request.amount(), "콘서트 티켓"));

        PaymentStatus status = pgResponse.pending()
            ? PaymentStatus.PENDING_CONFIRMATION
            : (pgResponse.success() ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);

        Payment payment = Payment.builder()
            .reservation(reservation)
            .amount(request.amount())
            .status(status)
            .idempotencyKey(idempotencyKey)
            .pgTransactionId(pgResponse.transactionId())
            .build();

        Payment saved = paymentRepository.save(payment);

        if (status == PaymentStatus.SUCCESS) {
            reservationService.confirmPayment(reservation.getId());
            outboxWriter.write("Payment", saved.getId(), "PaymentCompleted",
                Map.of("paymentId", saved.getId(), "reservationId", reservation.getId()));
        }

        PaymentResponse response = PaymentResponse.from(saved);
        cacheIdempotencyResponse(idempotencyKey, response);

        log.info("결제 처리 완료: paymentId={}, status={}", saved.getId(), status);
        return response;
    }

    private void cacheIdempotencyResponse(String key, PaymentResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(IDEMPOTENCY_PREFIX + key, json, IDEMPOTENCY_TTL);
        } catch (Exception e) {
            log.warn("Idempotency Redis 캐시 저장 실패: key={}", key, e);
        }
    }
}
