package com.api.backend.reservation.api;

import com.api.backend.global.response.ApiResponse;
import com.api.backend.reservation.api.dto.ReservationRequest;
import com.api.backend.reservation.api.dto.ReservationResponse;
import com.api.backend.reservation.application.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ApiResponse<ReservationResponse> createReservation(
        @Valid @RequestBody ReservationRequest request,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {

        return ApiResponse.ok(reservationService.createReservation(
            request.userId(), request.seatId(), request.concertId(), idempotencyKey));
    }

    @DeleteMapping("/{reservationId}")
    public ApiResponse<Void> cancelReservation(@PathVariable Long reservationId) {
        reservationService.cancelReservation(reservationId);
        return ApiResponse.ok();
    }
}
