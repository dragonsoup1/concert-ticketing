package com.api.backend.concert.application;

import com.api.backend.concert.api.dto.ConcertResponse;
import com.api.backend.concert.api.dto.SeatResponse;
import com.api.backend.concert.domain.ConcertRepository;
import com.api.backend.concert.domain.SeatRepository;
import com.api.backend.global.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConcertService {

    private final ConcertRepository concertRepository;
    private final SeatRepository seatRepository;

    public Page<ConcertResponse> getUpcomingConcerts(Pageable pageable) {
        return concertRepository
            .findByEventAtAfterOrderByEventAtAsc(LocalDateTime.now(), pageable)
            .map(ConcertResponse::from);
    }

    @Cacheable(value = "concert", key = "#id")
    public ConcertResponse getConcert(Long id) {
        return concertRepository.findById(id)
            .map(ConcertResponse::from)
            .orElseThrow(() -> new EntityNotFoundException("공연을 찾을 수 없습니다. id=" + id));
    }

    @Cacheable(value = "seats", key = "#concertId")
    public List<SeatResponse> getAvailableSeats(Long concertId) {
        return seatRepository.findAvailableByConcertId(concertId).stream()
            .map(SeatResponse::from)
            .toList();
    }

    @CacheEvict(value = "seats", key = "#concertId")
    public void evictSeatCache(Long concertId) {
        // 좌석 상태 변경 시 캐시 무효화
    }
}
