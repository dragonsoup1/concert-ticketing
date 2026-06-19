package com.api.backend.payment;

import com.api.backend.concert.domain.Seat;
import com.api.backend.concert.domain.SeatGrade;
import com.api.backend.concert.domain.SeatStatus;
import com.api.backend.payment.api.dto.PaymentRequest;
import com.api.backend.payment.api.dto.PaymentResponse;
import com.api.backend.outbox.application.OutboxWriter;
import com.api.backend.payment.application.PaymentGatewayClient;
import com.api.backend.payment.application.PaymentService;
import com.api.backend.payment.application.dto.PgResponse;
import com.api.backend.payment.domain.Payment;
import com.api.backend.payment.domain.PaymentRepository;
import com.api.backend.payment.domain.PaymentStatus;
import com.api.backend.reservation.application.ReservationService;
import com.api.backend.reservation.domain.Reservation;
import com.api.backend.reservation.domain.ReservationRepository;
import com.api.backend.reservation.domain.ReservationStatus;
import com.api.backend.user.domain.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("결제 서비스 멱등성 테스트")
class PaymentServiceIdempotencyTest {

    @InjectMocks PaymentService paymentService;

    @Mock PaymentRepository paymentRepository;
    @Mock ReservationRepository reservationRepository;
    @Mock ReservationService reservationService;
    @Mock PaymentGatewayClient paymentGatewayClient;
    @Mock OutboxWriter outboxWriter;
    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, Object> valueOps;
    @Spy  ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("동일 Idempotency-Key 두 번 요청 시 PG는 1회만 호출된다")
    void 동일_멱등성키_PG_1회만_호출() {
        String idempotencyKey = "pay-unique-key-001";
        PaymentRequest request = new PaymentRequest(1L, BigDecimal.valueOf(150_000));

        Seat seat = Seat.builder()
            .concert(null).seatNumber("A-01").grade(SeatGrade.VIP)
            .price(BigDecimal.valueOf(150_000)).status(SeatStatus.HELD).build();
        User user = User.builder().id(1L).email("a@b.com").name("테스터").build();
        Reservation reservation = Reservation.builder()
            .id(1L).user(user).seat(seat)
            .status(ReservationStatus.PENDING)
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .idempotencyKey(idempotencyKey)
            .build();

        Payment payment = Payment.builder()
            .id(1L).reservation(reservation)
            .amount(request.amount())
            .status(PaymentStatus.SUCCESS)
            .idempotencyKey(idempotencyKey)
            .pgTransactionId("pg-tx-001")
            .build();

        // 첫 번째 호출 — DB에 없음, PG 호출 성공
        given(paymentRepository.findByIdempotencyKey(idempotencyKey))
            .willReturn(Optional.empty())      // 첫 번째
            .willReturn(Optional.of(payment)); // 두 번째
        given(reservationRepository.findByIdWithDetails(1L)).willReturn(Optional.of(reservation));
        given(paymentGatewayClient.charge(any())).willReturn(PgResponse.success("pg-tx-001"));
        given(paymentRepository.save(any())).willReturn(payment);
        given(redisTemplate.opsForValue()).willReturn(valueOps);

        PaymentResponse first  = paymentService.processPayment(request, idempotencyKey);

        // 두 번째 호출 — DB에 이미 존재 → AlreadyProcessedPaymentException 발생
        // (컨트롤러에서 catch하고 동일 응답 반환)
        try {
            paymentService.processPayment(request, idempotencyKey);
        } catch (com.api.backend.payment.application.AlreadyProcessedPaymentException e) {
            assertThat(e.getPayment().getIdempotencyKey()).isEqualTo(idempotencyKey);
        }

        // PG 클라이언트는 단 1회만 호출됐어야 한다
        verify(paymentGatewayClient, times(1)).charge(any());
        assertThat(first.status()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("PG Circuit Breaker 발동 시 PENDING_CONFIRMATION 상태로 저장된다")
    void PG_장애_시_PENDING_CONFIRMATION_상태() {
        String key = "pay-cb-key";
        PaymentRequest request = new PaymentRequest(1L, BigDecimal.valueOf(150_000));

        Seat seat = Seat.builder()
            .concert(null).seatNumber("A-01").grade(SeatGrade.VIP)
            .price(BigDecimal.valueOf(150_000)).status(SeatStatus.HELD).build();
        User user = User.builder().id(1L).email("a@b.com").name("테스터").build();
        Reservation reservation = Reservation.builder()
            .id(1L).user(user).seat(seat)
            .status(ReservationStatus.PENDING)
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .idempotencyKey(key)
            .build();

        given(paymentRepository.findByIdempotencyKey(key)).willReturn(Optional.empty());
        given(reservationRepository.findByIdWithDetails(1L)).willReturn(Optional.of(reservation));
        given(paymentGatewayClient.charge(any())).willReturn(PgResponse.pendingFallback());
        given(redisTemplate.opsForValue()).willReturn(valueOps);

        // save 호출 시 인자를 그대로 반환하도록 캡처
        given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = paymentService.processPayment(request, key);

        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING_CONFIRMATION);
        // PENDING_CONFIRMATION이면 예매 confirm 호출하지 않음
        verify(reservationService, never()).confirmPayment(any());
    }

    @Test
    @DisplayName("PG FAILED 응답 시 FAILED 상태로 저장되고 Outbox 이벤트가 발행되지 않는다")
    void PG_FAILED_응답_시_FAILED_상태_Outbox_미발행() {
        String key = "pay-fail-key";
        PaymentRequest request = new PaymentRequest(1L, BigDecimal.valueOf(150_000));

        Seat seat = Seat.builder()
            .concert(null).seatNumber("A-01").grade(SeatGrade.VIP)
            .price(BigDecimal.valueOf(150_000)).status(SeatStatus.HELD).build();
        User user = User.builder().id(1L).email("a@b.com").name("테스터").build();
        Reservation reservation = Reservation.builder()
            .id(1L).user(user).seat(seat)
            .status(ReservationStatus.PENDING)
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .idempotencyKey(key)
            .build();

        given(paymentRepository.findByIdempotencyKey(key)).willReturn(Optional.empty());
        given(reservationRepository.findByIdWithDetails(1L)).willReturn(Optional.of(reservation));
        // success=false, pending=false → FAILED
        given(paymentGatewayClient.charge(any())).willReturn(new PgResponse(false, null, false));
        given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(redisTemplate.opsForValue()).willReturn(valueOps);

        PaymentResponse response = paymentService.processPayment(request, key);

        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        verify(reservationService, never()).confirmPayment(any());
        verify(outboxWriter, never()).write(any(), any(), any(), any());
    }

    @Test
    @DisplayName("PG SUCCESS 시 Outbox 이벤트가 정확히 1회 발행된다")
    void PG_SUCCESS_시_Outbox_1회_발행() {
        String key = "pay-success-outbox";
        PaymentRequest request = new PaymentRequest(2L, BigDecimal.valueOf(80_000));

        Seat seat = Seat.builder()
            .concert(null).seatNumber("C-03").grade(SeatGrade.GENERAL)
            .price(BigDecimal.valueOf(80_000)).status(SeatStatus.HELD).build();
        User user = User.builder().id(2L).email("b@b.com").name("유저2").build();
        Reservation reservation = Reservation.builder()
            .id(2L).user(user).seat(seat)
            .status(ReservationStatus.PENDING)
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .idempotencyKey(key)
            .build();
        Payment payment = Payment.builder()
            .id(2L).reservation(reservation)
            .amount(request.amount()).status(PaymentStatus.SUCCESS)
            .idempotencyKey(key).pgTransactionId("pg-tx-002").build();

        given(paymentRepository.findByIdempotencyKey(key)).willReturn(Optional.empty());
        given(reservationRepository.findByIdWithDetails(2L)).willReturn(Optional.of(reservation));
        given(paymentGatewayClient.charge(any())).willReturn(PgResponse.success("pg-tx-002"));
        given(paymentRepository.save(any())).willReturn(payment);
        given(redisTemplate.opsForValue()).willReturn(valueOps);

        paymentService.processPayment(request, key);

        verify(outboxWriter, times(1)).write(
            eq("Payment"), eq(2L), eq("PaymentCompleted"), any());
        verify(reservationService, times(1)).confirmPayment(2L);
    }
}
