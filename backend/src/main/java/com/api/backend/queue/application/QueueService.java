package com.api.backend.queue.application;

import com.api.backend.global.exception.QueueNotAdmittedException;
import com.api.backend.queue.api.dto.QueueStatusResponse;
import com.api.backend.queue.api.dto.TokenIssueResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class QueueService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${queue.batch-size:50}")
    private int batchSize;

    @Value("${queue.admission-ttl-minutes:10}")
    private int admissionTtlMinutes;

    private static final String WAITING_KEY_PREFIX  = "waiting:";
    private static final String ADMITTED_KEY_PREFIX = "admitted:";

    public TokenIssueResponse issueToken(Long userId, Long concertId) {
        String key   = waitingKey(concertId);
        double score = System.currentTimeMillis();

        redisTemplate.opsForZSet().addIfAbsent(key, userId.toString(), score);

        Long rank = redisTemplate.opsForZSet().rank(key, userId.toString());
        long safeRank = rank != null ? rank : 0L;

        return new TokenIssueResponse(userId, concertId, safeRank, estimateWait(safeRank));
    }

    public QueueStatusResponse getStatus(Long userId, Long concertId) {
        if (isAdmitted(userId, concertId)) {
            return QueueStatusResponse.ofAdmitted();
        }

        Long rank = redisTemplate.opsForZSet().rank(waitingKey(concertId), userId.toString());
        long safeRank = rank != null ? rank : 0L;
        return QueueStatusResponse.ofWaiting(safeRank, batchSize);
    }

    public void admitBatch(Long concertId) {
        String waitingKey = waitingKey(concertId);
        Set<ZSetOperations.TypedTuple<Object>> admitted =
            redisTemplate.opsForZSet().popMin(waitingKey, batchSize);

        if (admitted == null) return;

        admitted.stream()
            .map(ZSetOperations.TypedTuple::getValue)
            .filter(v -> v != null)
            .forEach(userId ->
                redisTemplate.opsForValue().set(
                    admittedKey(userId.toString(), concertId),
                    "1",
                    Duration.ofMinutes(admissionTtlMinutes)
                )
            );
    }

    public void assertAdmitted(Long userId, Long concertId) {
        if (!isAdmitted(userId, concertId)) {
            throw new QueueNotAdmittedException();
        }
    }

    private boolean isAdmitted(Long userId, Long concertId) {
        return Boolean.TRUE.equals(
            redisTemplate.hasKey(admittedKey(userId.toString(), concertId)));
    }

    private long estimateWait(long rank) {
        return (rank / batchSize) + 1;
    }

    private String waitingKey(Long concertId) {
        return WAITING_KEY_PREFIX + concertId;
    }

    private String admittedKey(String userId, Long concertId) {
        return ADMITTED_KEY_PREFIX + userId + ":" + concertId;
    }
}
