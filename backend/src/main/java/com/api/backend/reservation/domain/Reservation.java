package com.api.backend.reservation.domain;

import com.api.backend.concert.domain.Seat;
import com.api.backend.user.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "reservations", indexes = {
    @Index(name = "idx_res_user",          columnList = "user_id"),
    @Index(name = "idx_res_status_expiry", columnList = "status, expires_at")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(unique = true)
    private String idempotencyKey;

    @Version
    private Long version;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public static Reservation pending(User user, Seat seat, String idempotencyKey) {
        return Reservation.builder()
            .user(user)
            .seat(seat)
            .status(ReservationStatus.PENDING)
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .idempotencyKey(idempotencyKey)
            .build();
    }

    public void confirm() {
        transition(ReservationStatus.CONFIRMED);
    }

    public void markPaid() {
        transition(ReservationStatus.PAID);
    }

    public void cancel() {
        transition(ReservationStatus.CANCELLED);
    }

    public void expire() {
        transition(ReservationStatus.EXPIRED);
    }

    private void transition(ReservationStatus next) {
        if (!this.status.canTransitionTo(next)) {
            throw new IllegalStateException(
                "예약 상태 전환 불가: " + this.status + " → " + next);
        }
        this.status = next;
    }
}
