package com.api.backend.queue.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class QueueScheduler {

    private final QueueService queueService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String WAITING_KEY_PATTERN = "waiting:*";

    @Scheduled(fixedDelay = 1000)
    public void admitNextBatch() {
        Set<String> keys = redisTemplate.keys(WAITING_KEY_PATTERN);
        if (keys == null || keys.isEmpty()) return;

        keys.forEach(key -> {
            Long concertId = extractConcertId(key);
            if (concertId != null) {
                queueService.admitBatch(concertId);
            }
        });
    }

    private Long extractConcertId(String key) {
        try {
            return Long.parseLong(key.substring("waiting:".length()));
        } catch (NumberFormatException e) {
            log.warn("Invalid queue key format: {}", key);
            return null;
        }
    }
}
