package com.api.backend.concert.api;

import com.api.backend.concert.api.dto.ConcertResponse;
import com.api.backend.concert.api.dto.SeatResponse;
import com.api.backend.concert.application.ConcertService;
import com.api.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/concerts")
@RequiredArgsConstructor
public class ConcertController {

    private final ConcertService concertService;

    @GetMapping
    public ApiResponse<Page<ConcertResponse>> getUpcomingConcerts(
        @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(concertService.getUpcomingConcerts(pageable));
    }

    @GetMapping("/{concertId}")
    public ApiResponse<ConcertResponse> getConcert(@PathVariable Long concertId) {
        return ApiResponse.ok(concertService.getConcert(concertId));
    }

    @GetMapping("/{concertId}/seats")
    public ApiResponse<List<SeatResponse>> getAvailableSeats(@PathVariable Long concertId) {
        return ApiResponse.ok(concertService.getAvailableSeats(concertId));
    }
}
