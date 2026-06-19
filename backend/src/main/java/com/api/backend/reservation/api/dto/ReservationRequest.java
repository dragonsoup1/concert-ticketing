package com.api.backend.reservation.api.dto;

import jakarta.validation.constraints.NotNull;

public record ReservationRequest(
    @NotNull Long userId,
    @NotNull Long seatId,
    @NotNull Long concertId
) {}
