package com.api.backend.reservation;

import com.api.backend.concert.application.ConcertService;
import com.api.backend.concert.domain.Seat;
import com.api.backend.concert.domain.SeatGrade;
import com.api.backend.concert.domain.SeatStatus;
import com.api.backend.global.exception.EntityNotFoundException;
import com.api.backend.outbox.application.OutboxWriter;
import com.api.backend.queue.application.QueueService;
import com.api.backend.reservation.application.ReservationService;
import com.api.backend.reservation.domain.Reservation;
import com.api.backend.reservation.domain.ReservationRepository;
import com.api.backend.reservation.domain.ReservationStatus;
import com.api.backend.user.domain.User;
import com.api.backend.user.domain.UserRepository;
import com.api.backend.concert.domain.Concert;
import com.api.backend.concert.domain.SeatRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("예매 서비스 단위 테스트")
class ReservationServiceTest {

    @InjectMocks ReservationService reservationService;

    @Mock ReservationRepository reservationRepository;
    @Mock SeatRepository seatRepository;
    @Mock UserRepository userRepository;
    @Mock QueueService queueService;
    @Mock ConcertService concertService;
    @Mock OutboxWriter outboxWriter;

    // ── 공통 픽스처 ──────────────────────────────────────────────────────────

    private Concert concert() {
        return Concert.builder().id(1L).title("테스트 콘서트").venue("올림픽홀")
            .eventAt(LocalDateTime.now().plusDays(30)).totalSeats(100).build();
    }

    private Seat heldSeat() {
        return Seat.builder().id(10L).concert(concert())
            .seatNumber("B-01").grade(SeatGrade.GENERAL).price(BigDecimal.valueOf(100_000))
            .status(SeatStatus.HELD).build();
    }

    private User user() {
        return User.builder().id(1L).email("u@test.com").name("테스터").build();
    }

    private Reservation pendingReservation(Seat seat) {
        return Reservation.builder()
            .id(99L).user(user()).seat(seat)
            .status(ReservationStatus.PENDING)
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .idempotencyKey("idem-cancel-001")
            .build();
    }

    // ── cancelReservation ────────────────────────────────────────────────────

    @Test
    @DisplayName("예매 취소 시 좌석이 AVAILABLE로 해제되고 Outbox 이벤트가 기록된다")
    void 예매_취소_좌석해제_Outbox기록() {
        Seat seat = heldSeat();
        Reservation reservation = pendingReservation(seat);

        given(reservationRepository.findByIdWithDetails(99L))
            .willReturn(Optional.of(reservation));

        reservationService.cancelReservation(99L);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        verify(concertService).evictSeatCache(concert().getId());
        verify(outboxWriter).write(eq("Reservation"), eq(99L), eq("ReservationCancelled"), any());
    }

    @Test
    @DisplayName("존재하지 않는 예매 취소 시 EntityNotFoundException")
    void 없는_예매_취소_예외() {
        given(reservationRepository.findByIdWithDetails(anyLong()))
            .willReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.cancelReservation(999L))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("CONFIRMED 예매도 취소 가능하다")
    void CONFIRMED_예매_취소_가능() {
        Seat seat = heldSeat();
        Reservation reservation = Reservation.builder()
            .id(99L).user(user()).seat(seat)
            .status(ReservationStatus.CONFIRMED)
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .idempotencyKey("idem-cancel-002")
            .build();

        given(reservationRepository.findByIdWithDetails(99L))
            .willReturn(Optional.of(reservation));

        reservationService.cancelReservation(99L);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    // ── expireReservations ───────────────────────────────────────────────────

    @Test
    @DisplayName("만료된 PENDING 예매를 EXPIRED 처리하고 좌석을 해제한다")
    void 만료_예매_EXPIRED_처리() {
        Seat seat = heldSeat();
        Reservation expired1 = Reservation.builder()
            .id(1L).user(user()).seat(seat)
            .status(ReservationStatus.PENDING)
            .expiresAt(LocalDateTime.now().minusMinutes(1))
            .idempotencyKey("idem-exp-001")
            .build();

        given(reservationRepository.findExpired(any())).willReturn(List.of(expired1));

        reservationService.expireReservations();

        assertThat(expired1.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        verify(outboxWriter).write(eq("Reservation"), eq(1L), eq("ReservationExpired"), any());
    }

    @Test
    @DisplayName("만료된 예매가 없으면 아무 처리도 하지 않는다")
    void 만료_예매_없음_처리_없음() {
        given(reservationRepository.findExpired(any())).willReturn(List.of());

        reservationService.expireReservations();

        verify(outboxWriter, never()).write(anyString(), anyLong(), anyString(), any());
    }

    // ── confirmPayment ───────────────────────────────────────────────────────

    @Test
    @DisplayName("결제 확인 시 예매가 CONFIRMED되고 좌석이 SOLD된다")
    void 결제_확인_CONFIRMED_SOLD() {
        Seat seat = heldSeat();
        Reservation reservation = pendingReservation(seat);

        given(reservationRepository.findById(99L)).willReturn(Optional.of(reservation));

        reservationService.confirmPayment(99L);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.SOLD);
    }

    @Test
    @DisplayName("존재하지 않는 예매 confirmPayment 시 EntityNotFoundException")
    void 없는_예매_confirmPayment_예외() {
        given(reservationRepository.findById(anyLong())).willReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.confirmPayment(999L))
            .isInstanceOf(EntityNotFoundException.class);
    }
}
