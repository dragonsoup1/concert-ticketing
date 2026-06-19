package com.api.backend.outbox;

import com.api.backend.outbox.application.OutboxRelayScheduler;
import com.api.backend.outbox.domain.OutboxEvent;
import com.api.backend.outbox.domain.OutboxEventRepository;
import com.api.backend.outbox.domain.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Outbox Relay 스케줄러 테스트")
class OutboxRelaySchedulerTest {

    // @InjectMocks 대신 직접 생성 — Spring AOP 프록시가 끼어드는 것을 막는다
    OutboxRelayScheduler scheduler;

    @Mock OutboxEventRepository outboxEventRepository;
    @Mock KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() {
        scheduler = new OutboxRelayScheduler(outboxEventRepository, kafkaTemplate);
    }

    @Test
    @DisplayName("PENDING 이벤트를 Kafka로 발행하고 PUBLISHED로 상태를 변경한다")
    void PENDING_이벤트_Kafka_발행_후_PUBLISHED() {
        OutboxEvent event = OutboxEvent.builder()
            .aggregateType("Reservation").aggregateId(1L)
            .eventType("ReservationCreated")
            .payload("{\"reservationId\":1}")
            .status(OutboxStatus.PENDING).retryCount(0)
            .build();

        given(outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
            .willReturn(List.of(event));
        given(kafkaTemplate.send(any(), any())).willReturn(null);

        scheduler.relay();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), any());
        assertThat(topicCaptor.getValue()).isEqualTo("ReservationCreated");
    }

    @Test
    @DisplayName("Kafka 발행 5회 실패 시 FAILED로 상태를 변경한다")
    void Kafka_5회_실패_시_FAILED() {
        OutboxEvent event = OutboxEvent.builder()
            .aggregateType("Payment").aggregateId(1L)
            .eventType("PaymentCompleted").payload("{}")
            .status(OutboxStatus.PENDING).retryCount(4)  // 이미 4번 시도됨
            .build();

        given(outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
            .willReturn(List.of(event));
        doThrow(new RuntimeException("Kafka 연결 실패"))
            .when(kafkaTemplate).send(any(), any());

        scheduler.relay();

        assertThat(event.getRetryCount()).isEqualTo(5);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }

    @Test
    @DisplayName("Kafka 발행 실패 시 retryCount가 증가한다")
    void Kafka_발행_실패_시_retryCount_증가() {
        OutboxEvent event = OutboxEvent.builder()
            .aggregateType("Reservation").aggregateId(2L)
            .eventType("ReservationCreated").payload("{}")
            .status(OutboxStatus.PENDING).retryCount(0)
            .build();

        given(outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
            .willReturn(List.of(event));
        doThrow(new RuntimeException("일시적 장애"))
            .when(kafkaTemplate).send(any(), any());

        scheduler.relay();

        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING); // FAILED 아님
    }
}
