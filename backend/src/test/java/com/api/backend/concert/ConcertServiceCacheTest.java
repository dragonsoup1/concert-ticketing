package com.api.backend.concert;

import com.api.backend.concert.application.ConcertService;
import com.api.backend.concert.domain.Concert;
import com.api.backend.concert.domain.ConcertRepository;
import com.api.backend.concert.domain.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.annotation.AnnotationCacheOperationSource;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheInterceptor;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheResolver;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("Concert 캐싱 테스트")
class ConcertServiceCacheTest {

    @Mock ConcertRepository concertRepository;
    @Mock SeatRepository seatRepository;

    private ConcertService concertService;
    private ConcurrentMapCacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager = new ConcurrentMapCacheManager("concert", "seats");

        // @EnableCaching이 있는 Spring context를 최소로 만들어 ConcertService AOP 프록시 생성
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean("concertRepository", ConcertRepository.class, () -> concertRepository);
        ctx.registerBean("seatRepository", SeatRepository.class, () -> seatRepository);
        ctx.registerBean(ConcertService.class);
        ctx.registerBean("cacheManager",
            ConcurrentMapCacheManager.class, () -> cacheManager);
        ctx.register(CacheConfig.class);
        ctx.refresh();

        concertService = ctx.getBean(ConcertService.class);
    }

    @Configuration
    @EnableCaching
    static class CacheConfig {}

    @Test
    @DisplayName("같은 공연 두 번 조회 시 Repository는 1회만 호출된다")
    void 동일_공연_두번_조회_캐시_히트() {
        Concert concert = Concert.builder()
            .title("세븐틴 콘서트").venue("KSPO DOME")
            .eventAt(LocalDateTime.now().plusDays(60))
            .totalSeats(5000)
            .build();

        given(concertRepository.findById(1L)).willReturn(Optional.of(concert));

        concertService.getConcert(1L); // 첫 번째 — DB 조회
        concertService.getConcert(1L); // 두 번째 — 캐시 히트

        verify(concertRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("evictSeatCache 호출 후 seats 캐시가 무효화된다")
    void 캐시_무효화_후_재조회() {
        given(seatRepository.findAvailableByConcertId(2L)).willReturn(List.of());

        concertService.getAvailableSeats(2L); // 첫 번째 — DB 조회 + 캐시 적재
        concertService.evictSeatCache(2L);     // 캐시 무효화
        concertService.getAvailableSeats(2L); // 두 번째 — 캐시 미스 → DB 재조회

        verify(seatRepository, times(2)).findAvailableByConcertId(2L);
    }
}
