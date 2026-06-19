package com.api.backend.outbox.application;

import com.api.backend.outbox.domain.OutboxEvent;
import com.api.backend.outbox.domain.OutboxEventRepository;
import com.api.backend.outbox.domain.OutboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final int MAX_RETRY = 5;

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void relay() {
        List<OutboxEvent> pending = outboxEventRepository
            .findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(event.getEventType(), event.getPayload());
                event.markPublished();
                log.debug("Outbox 발행 완료: type={}, id={}", event.getEventType(), event.getId());
            } catch (Exception e) {
                log.warn("Outbox 발행 실패: id={}, retry={}", event.getId(), event.getRetryCount(), e);
                event.incrementRetry();
                if (event.getRetryCount() >= MAX_RETRY) {
                    event.markFailed();
                }
            }
        }
    }
}
