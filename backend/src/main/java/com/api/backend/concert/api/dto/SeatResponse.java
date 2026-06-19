package com.api.backend.concert.api.dto;

import com.api.backend.concert.domain.Seat;
import com.api.backend.concert.domain.SeatGrade;
import com.api.backend.concert.domain.SeatStatus;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record SeatResponse(
    Long id,
    String seatNumber,
    SeatGrade grade,
    BigDecimal price,
    SeatStatus status
) {
    public static SeatResponse from(Seat seat) {
        return SeatResponse.builder()
            .id(seat.getId())
            .seatNumber(seat.getSeatNumber())
            .grade(seat.getGrade())
            .price(seat.getPrice())
            .status(seat.getStatus())
            .build();
    }
}
