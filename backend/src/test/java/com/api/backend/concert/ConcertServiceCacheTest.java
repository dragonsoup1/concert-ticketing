package com.api.backend.concert;

import com.api.backend.concert.application.ConcertService;
import com.api.backend.concert.domain.Concert;
import com.api.backend.concert.domain.ConcertRepository;
import com.api.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("Concert 캐싱 테스트")
class ConcertServiceCacheTest extends IntegrationTestSupport {

    @Autowired ConcertService concertService;
    @Autowired CacheManager cacheManager;

    @MockitoBean ConcertRepository concertRepository;
    @MockitoBean KafkaTemplate<String, String> kafkaTemplate;

    @AfterEach
    void tearDown() {
        cacheManager.getCache("concert").clear();
        cacheManager.getCache("seats").clear();
    }

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
        // seats 캐시에 임의 값 적재
        cacheManager.getCache("seats").put(2L, "dummy");
        assertThat(cacheManager.getCache("seats").get(2L)).isNotNull();

        concertService.evictSeatCache(2L);

        assertThat(cacheManager.getCache("seats").get(2L)).isNull();
    }
}
