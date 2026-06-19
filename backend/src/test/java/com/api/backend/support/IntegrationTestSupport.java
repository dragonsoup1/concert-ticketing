package com.api.backend.support;

import com.api.backend.outbox.application.OutboxRelayScheduler;
import com.api.backend.queue.application.QueueScheduler;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 통합 테스트 base class.
 * - PostgreSQL / Redis: TestContainerConfig의 싱글턴 컨테이너를 공유 — 모든 Spring 컨텍스트가 동일 인스턴스 사용
 * - KafkaTemplate / QueueScheduler / OutboxRelayScheduler: 공통 mock → 컨텍스트 캐시 공유 보장
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
public abstract class IntegrationTestSupport {

    @MockitoBean OutboxRelayScheduler outboxRelayScheduler;
    @MockitoBean QueueScheduler queueScheduler;
    @MockitoBean KafkaTemplate<String, String> kafkaTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      TestContainerConfig.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",  TestContainerConfig.POSTGRES::getUsername);
        registry.add("spring.datasource.password",  TestContainerConfig.POSTGRES::getPassword);
        registry.add("spring.data.redis.host",      TestContainerConfig.REDIS::getHost);
        registry.add("spring.data.redis.port",
            () -> String.valueOf(TestContainerConfig.REDIS.getMappedPort(6379)));
    }
}
