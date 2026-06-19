package com.api.backend.reservation;

import com.api.backend.concert.domain.*;
import com.api.backend.queue.application.QueueService;
import com.api.backend.reservation.application.ReservationService;
import com.api.backend.reservation.domain.ReservationRepository;
import com.api.backend.reservation.domain.ReservationStatus;
import com.api.backend.support.IntegrationTestSupport;
import com.api.backend.user.domain.User;
import com.api.backend.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

@DisplayName("예매 동시성 테스트")
class ReservationConcurrencyTest extends IntegrationTestSupport {

    @Autowired private ReservationService reservationService;
    @Autowired private UserRepository userRepository;
    @Autowired private ConcertRepository concertRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private ReservationRepository reservationRepository;

    @MockitoBean QueueService queueService;
    @MockitoBean KafkaTemplate<String, String> kafkaTemplate;

    private User user;
    private Concert concert;
    private Seat seat;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        seatRepository.deleteAll();
        concertRepository.deleteAll();
        userRepository.deleteAll();

        user = userRepository.save(User.builder()
            .email("test@example.com")
            .name("테스터")
            .build());

        concert = concertRepository.save(Concert.builder()
            .title("아이유 콘서트")
            .venue("올림픽홀")
            .eventAt(LocalDateTime.now().plusDays(30))
            .totalSeats(1)
            .build());

        seat = seatRepository.save(Seat.builder()
            .concert(concert)
            .seatNumber("A-01")
            .grade(SeatGrade.VIP)
            .price(BigDecimal.valueOf(150_000))
            .status(SeatStatus.AVAILABLE)
            .build());

        doNothing().when(queueService).assertAdmitted(any(), any());
    }

    @Test
    @DisplayName("100개 동시 요청 중 정확히 1개만 예매 성공 — 분산락 + Optimistic Lock 이중 방어")
    void 동시_100명_예매_요청_중_1명만_성공() throws InterruptedException {
        int threadCount = 100;
        CountDownLatch ready  = new CountDownLatch(threadCount);
        CountDownLatch start  = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount    = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final String key = "idem-" + UUID.randomUUID();
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    reservationService.createReservation(
                        user.getId(), seat.getId(), concert.getId(), key);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    finish.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        finish.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get())
            .as("정확히 1개만 예매에 성공해야 한다")
            .isEqualTo(1);
        assertThat(failCount.get())
            .as("나머지 99개는 실패해야 한다")
            .isEqualTo(threadCount - 1);

        long pendingCount = reservationRepository.findAll().stream()
            .filter(r -> r.getStatus() == ReservationStatus.PENDING)
            .count();
        assertThat(pendingCount).isEqualTo(1);
    }
}
