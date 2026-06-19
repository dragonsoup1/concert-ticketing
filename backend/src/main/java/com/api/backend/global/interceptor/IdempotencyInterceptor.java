package com.api.backend.global.interceptor;

import com.api.backend.global.exception.MissingIdempotencyKeyException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyInterceptor implements HandlerInterceptor {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        if (!isPaymentRequest(request)) return true;

        String key = request.getHeader(IDEMPOTENCY_HEADER);
        if (key == null || key.isBlank()) {
            throw new MissingIdempotencyKeyException();
        }

        Object cached = redisTemplate.opsForValue().get(IDEMPOTENCY_KEY_PREFIX + key);
        if (cached != null) {
            log.info("Idempotency cache hit: key={}", key);
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Idempotent-Replayed", "true");
            response.getWriter().write(cached.toString());
            return false;
        }

        return true;
    }

    private boolean isPaymentRequest(HttpServletRequest request) {
        return "POST".equals(request.getMethod())
            && request.getRequestURI().startsWith("/api/payments");
    }
}
