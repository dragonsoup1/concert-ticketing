package com.api.backend;

import com.api.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.kafka.core.KafkaTemplate;

class BackendApplicationTests extends IntegrationTestSupport {

    @MockitoBean
    KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void contextLoads() {
    }
}
