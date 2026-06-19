package com.api.backend.reservation.application;

import com.api.backend.concert.application.ConcertService;
import com.api.backend.concert.domain.Seat;
import com.api.backend.concert.domain.SeatRepository;
import com.api.backend.global.exception.EntityNotFoundException;
import com.api.backend.global.lock.DistributedLock;
import com.api.backend.outbox.application.OutboxWriter;
import com.api.backend.queue.application.QueueService;
import com.api.backend.reservation.api.dto.ReservationResponse;
import com.api.backend.reservation.domain.Reservation;
import com.api.backend.reservation.domain.ReservationRepository;
import com.api.backend.reservation.domain.ReservationStatus;
import com.api.backend.user.domain.User;
import com.api.backend.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;
    private final QueueService queueService;
    private final ConcertService concertService;
    private final OutboxWriter outboxWriter;

    /**
     * 분산락(Redisson) → Optimistic Lock(@Version) 이중 방어로 동시 예매 방지.
     *
     * - 분산락: 멀티 JVM 환경에서 seat별 크리티컬 섹션 보장
     * - OL(@Version): 분산락 TTL 만료 등 극단적 케이스 최종 안전망
     */
    @DistributedLock(key = "'seat:lock:' + #seatId", leaseTime = 3)
    @Transactional
    public ReservationResponse createReservation(Long userId, Long seatId,
                                                 Long concertId, String idempotencyKey) {
        queueService.assertAdmitted(userId, concertId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));

        Seat seat = seatRepository.findById(seatId)
            .orElseThrow(() -> new EntityNotFoundException("좌석을 찾을 수 없습니다."));

        seat.hold();  // AVAILABLE → HELD, @Version 증가 → 동시 커밋 시 OptimisticLockException

        Reservation reservation = Reservation.pending(user, seat, idempotencyKey);
        reservationRepository.save(reservation);

        outboxWriter.write("Reservation", reservation.getId(),
            "ReservationCreated", Map.of("reservationId", reservation.getId(), "seatId", seatId));

        concertService.evictSeatCache(concertId);

        log.info("예매 생성 완료: reservationId={}, userId={}, seatId={}", reservation.getId(), userId, seatId);
        return ReservationResponse.from(reservation);
    }

    @Transactional
    public void cancelReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findByIdWithDetails(reservationId)
            .orElseThrow(() -> new EntityNotFoundException("예약을 찾을 수 없습니다."));

        reservation.cancel();
        reservation.getSeat().release();

        concertService.evictSeatCache(reservation.getSeat().getConcert().getId());

        outboxWriter.write("Reservation", reservationId, "ReservationCancelled",
            Map.of("reservationId", reservationId));
    }

    /** 만료된 PENDING 예매를 주기적으로 정리하고 좌석을 해제한다. */
    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void expireReservations() {
        List<Reservation> expired = reservationRepository.findExpired(LocalDateTime.now());
        for (Reservation r : expired) {
            r.expire();
            r.getSeat().release();
            outboxWriter.write("Reservation", r.getId(), "ReservationExpired",
                Map.of("reservationId", r.getId()));
            log.info("예매 만료 처리: reservationId={}", r.getId());
        }
    }

    @Transactional
    public void confirmPayment(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
            .orElseThrow(() -> new EntityNotFoundException("예약을 찾을 수 없습니다."));
        reservation.confirm();
        reservation.getSeat().sell();
    }
}
