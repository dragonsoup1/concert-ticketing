package com.api.backend.outbox.application;

import com.api.backend.outbox.domain.OutboxEvent;
import com.api.backend.outbox.domain.OutboxEventRepository;
import com.api.backend.outbox.domain.OutboxStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void write(String aggregateType, Long aggregateId, String eventType, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(json)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .build());
        } catch (JsonProcessingException e) {
            log.error("Outbox 직렬화 실패: aggregateType={}, id={}", aggregateType, aggregateId, e);
            throw new RuntimeException("Outbox 이벤트 직렬화 실패", e);
        }
    }
}
