package com.api.backend.concert.api.dto;

import com.api.backend.concert.domain.Concert;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ConcertResponse(
    Long id,
    String title,
    String venue,
    LocalDateTime eventAt,
    Integer totalSeats
) {
    public static ConcertResponse from(Concert concert) {
        return ConcertResponse.builder()
            .id(concert.getId())
            .title(concert.getTitle())
            .venue(concert.getVenue())
            .eventAt(concert.getEventAt())
            .totalSeats(concert.getTotalSeats())
            .build();
    }
}
