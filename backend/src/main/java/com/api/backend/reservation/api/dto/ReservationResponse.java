package com.api.backend.reservation.api.dto;

import com.api.backend.reservation.domain.Reservation;
import com.api.backend.reservation.domain.ReservationStatus;

import java.time.LocalDateTime;

public record ReservationResponse(
    Long id,
    Long userId,
    Long seatId,
    ReservationStatus status,
    LocalDateTime expiresAt
) {
    public static ReservationResponse from(Reservation r) {
        return new ReservationResponse(
            r.getId(),
            r.getUser().getId(),
            r.getSeat().getId(),
            r.getStatus(),
            r.getExpiresAt()
        );
    }
}
