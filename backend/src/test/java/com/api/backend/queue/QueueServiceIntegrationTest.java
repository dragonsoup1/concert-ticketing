package com.api.backend.queue;

import com.api.backend.global.exception.QueueNotAdmittedException;
import com.api.backend.queue.api.dto.QueueStatusResponse;
import com.api.backend.queue.api.dto.TokenIssueResponse;
import com.api.backend.queue.application.QueueService;
import com.api.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("대기열 서비스 통합 테스트")
class QueueServiceIntegrationTest extends IntegrationTestSupport {

    @Autowired QueueService queueService;
    @Autowired RedisTemplate<String, Object> redisTemplate;

    private static final Long CONCERT_ID = 1L;

    @BeforeEach
    void setUp() {
        redisTemplate.delete("waiting:" + CONCERT_ID);
        for (long i = 1; i <= 200; i++) {
            redisTemplate.delete("admitted:" + i + ":" + CONCERT_ID);
        }
    }

    @Test
    @DisplayName("토큰 발급 시 FIFO 순서(score=enqueueTime)가 보장된다")
    void 토큰_발급_FIFO_순서_보장() throws InterruptedException {
        int userCount = 10;
        List<TokenIssueResponse> responses = new ArrayList<>();

        for (long userId = 1; userId <= userCount; userId++) {
            responses.add(queueService.issueToken(userId, CONCERT_ID));
            Thread.sleep(1);
        }

        for (int i = 0; i < responses.size(); i++) {
            assertThat(responses.get(i).rank())
                .as("userId=%d 는 rank=%d 이어야 한다", i + 1, i)
                .isEqualTo((long) i);
        }
    }

    @Test
    @DisplayName("같은 유저가 중복 발급 시 rank가 변하지 않는다")
    void 중복_토큰_발급_rank_불변() {
        queueService.issueToken(1L, CONCERT_ID);
        TokenIssueResponse second = queueService.issueToken(1L, CONCERT_ID);

        assertThat(second.rank()).isEqualTo(0L);
    }

    @Test
    @DisplayName("배치 입장 후 50명은 admitted, 이후 대기자는 waiting 상태")
    void 배치_입장_후_admitted_상태() {
        for (long userId = 1; userId <= 60; userId++) {
            queueService.issueToken(userId, CONCERT_ID);
        }

        queueService.admitBatch(CONCERT_ID); // batchSize=50 → 50명 입장

        QueueStatusResponse admitted = queueService.getStatus(1L, CONCERT_ID);
        assertThat(admitted.admitted()).isTrue();

        QueueStatusResponse waiting = queueService.getStatus(60L, CONCERT_ID);
        assertThat(waiting.admitted()).isFalse();
    }

    @Test
    @DisplayName("입장 패스 없는 유저의 assertAdmitted는 QueueNotAdmittedException을 던진다")
    void 미입장_유저_assertAdmitted_예외() {
        assertThatThrownBy(() -> queueService.assertAdmitted(999L, CONCERT_ID))
            .isInstanceOf(QueueNotAdmittedException.class);
    }
}
