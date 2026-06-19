package com.api.backend.outbox;

import com.api.backend.outbox.application.OutboxWriter;
import com.api.backend.outbox.domain.OutboxEvent;
import com.api.backend.outbox.domain.OutboxEventRepository;
import com.api.backend.outbox.domain.OutboxStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("Outbox Writer 테스트")
class OutboxWriterTest {

    @InjectMocks OutboxWriter outboxWriter;

    @Mock OutboxEventRepository outboxEventRepository;
    @Spy  ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("정상 호출 시 PENDING 상태의 OutboxEvent가 저장된다")
    void 정상_호출_PENDING_이벤트_저장() {
        given(outboxEventRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        outboxWriter.write("Reservation", 1L, "ReservationCreated",
            Map.of("reservationId", 1L, "seatId", 10L));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getAggregateType()).isEqualTo("Reservation");
        assertThat(saved.getAggregateId()).isEqualTo(1L);
        assertThat(saved.getEventType()).isEqualTo("ReservationCreated");
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getPayload()).contains("reservationId");
    }

    @Test
    @DisplayName("payload 직렬화 실패 시 RuntimeException이 발생한다")
    void payload_직렬화_실패_RuntimeException() {
        // ObjectMapper가 직렬화할 수 없는 객체 (순환 참조)
        Object unserializable = new Object() {
            public Object self = this; // 순환 참조
        };

        assertThatThrownBy(() ->
            outboxWriter.write("Test", 1L, "TestEvent", unserializable))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Outbox 이벤트 직렬화 실패");
    }
}
