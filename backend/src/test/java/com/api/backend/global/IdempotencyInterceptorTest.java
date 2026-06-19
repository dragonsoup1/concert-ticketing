package com.api.backend.global;

import com.api.backend.global.exception.MissingIdempotencyKeyException;
import com.api.backend.global.interceptor.IdempotencyInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("Idempotency 인터셉터 테스트")
class IdempotencyInterceptorTest {

    @InjectMocks IdempotencyInterceptor interceptor;

    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, Object> valueOps;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request  = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("결제 요청에 Idempotency-Key 헤더가 없으면 MissingIdempotencyKeyException")
    void 결제_요청_헤더_없음_예외() {
        request.setMethod("POST");
        request.setRequestURI("/api/payments");

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
            .isInstanceOf(MissingIdempotencyKeyException.class);
    }

    @Test
    @DisplayName("결제 요청에 빈 Idempotency-Key 헤더이면 MissingIdempotencyKeyException")
    void 결제_요청_빈_헤더_예외() {
        request.setMethod("POST");
        request.setRequestURI("/api/payments");
        request.addHeader("Idempotency-Key", "  ");

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
            .isInstanceOf(MissingIdempotencyKeyException.class);
    }

    @Test
    @DisplayName("Redis 캐시 히트 시 저장된 응답을 재생하고 false를 반환한다")
    void Redis_캐시_히트_응답_재생() throws Exception {
        request.setMethod("POST");
        request.setRequestURI("/api/payments");
        request.addHeader("Idempotency-Key", "replay-key-001");

        String cachedBody = "{\"success\":true,\"data\":{\"status\":\"SUCCESS\"}}";
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get("idempotency:replay-key-001")).willReturn(cachedBody);

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(response.getContentAsString()).isEqualTo(cachedBody);
    }

    @Test
    @DisplayName("Redis 캐시 미스 시 true를 반환하여 요청을 통과시킨다")
    void Redis_캐시_미스_요청_통과() throws Exception {
        request.setMethod("POST");
        request.setRequestURI("/api/payments");
        request.addHeader("Idempotency-Key", "new-key-001");

        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get("idempotency:new-key-001")).willReturn(null);

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isTrue();
    }

    @Test
    @DisplayName("결제 외 경로는 Idempotency-Key 없어도 통과한다")
    void 비결제_경로_헤더_없어도_통과() throws Exception {
        request.setMethod("POST");
        request.setRequestURI("/api/reservations");

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isTrue();
    }

    @Test
    @DisplayName("GET 결제 경로는 Idempotency-Key 없어도 통과한다")
    void GET_결제_경로_통과() throws Exception {
        request.setMethod("GET");
        request.setRequestURI("/api/payments/1");

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isTrue();
    }
}
