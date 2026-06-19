package com.api.backend.queue;

import com.api.backend.queue.application.QueueScheduler;
import com.api.backend.queue.application.QueueService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Set;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("대기열 스케줄러 테스트")
class QueueSchedulerTest {

    @InjectMocks QueueScheduler queueScheduler;

    @Mock QueueService queueService;
    @Mock RedisTemplate<String, Object> redisTemplate;

    @Test
    @DisplayName("waiting 키가 있으면 concertId별로 admitBatch를 호출한다")
    void waiting_키_존재_시_admitBatch_호출() {
        given(redisTemplate.keys("waiting:*"))
            .willReturn(Set.of("waiting:1", "waiting:2"));

        queueScheduler.admitNextBatch();

        verify(queueService).admitBatch(1L);
        verify(queueService).admitBatch(2L);
    }

    @Test
    @DisplayName("waiting 키가 없으면 admitBatch를 호출하지 않는다")
    void waiting_키_없음_admitBatch_미호출() {
        given(redisTemplate.keys("waiting:*")).willReturn(Set.of());

        queueScheduler.admitNextBatch();

        verify(queueService, never()).admitBatch(any());
    }

    @Test
    @DisplayName("keys()가 null을 반환해도 NPE 없이 종료된다")
    void keys_null_반환_NPE_없음() {
        given(redisTemplate.keys("waiting:*")).willReturn(null);

        queueScheduler.admitNextBatch();

        verify(queueService, never()).admitBatch(any());
    }

    @Test
    @DisplayName("잘못된 형식의 키는 무시하고 유효한 키만 처리한다")
    void 잘못된_키_형식_무시() {
        given(redisTemplate.keys("waiting:*"))
            .willReturn(Set.of("waiting:100", "waiting:invalid"));

        queueScheduler.admitNextBatch();

        verify(queueService).admitBatch(100L);
        verify(queueService, times(1)).admitBatch(any());
    }
}
