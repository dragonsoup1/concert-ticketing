package com.api.backend.global;

import com.api.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("Rate Limit 슬라이딩 윈도우 테스트")
class RateLimitFilterTest extends IntegrationTestSupport {

    @Autowired WebApplicationContext context;
    @Autowired RedisTemplate<String, Object> redisTemplate;

    @MockitoBean KafkaTemplate<String, String> kafkaTemplate;

    private MockMvc mockMvc;
    private static final String USER_ID = "rate-test-user-" + System.currentTimeMillis();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        redisTemplate.delete("rate:" + USER_ID);
    }

    @Test
    @DisplayName("1초 윈도우에서 10번까지는 성공, 11번째는 HTTP 429")
    void 슬라이딩_윈도우_10회_초과_시_429() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/concerts")
                    .header("X-User-Id", USER_ID))
                .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/concerts")
                .header("X-User-Id", USER_ID))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("X-User-Id 헤더 없으면 Rate Limit 적용 안 됨")
    void userId_헤더_없으면_rate_limit_미적용() throws Exception {
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(get("/api/concerts"))
                .andExpect(status().isOk());
        }
    }
}
