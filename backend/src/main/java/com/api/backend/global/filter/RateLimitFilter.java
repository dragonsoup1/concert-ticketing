package com.api.backend.global.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${rate-limit.window-millis:1000}")
    private long windowMillis;

    @Value("${rate-limit.max-requests:10}")
    private int maxRequests;

    /**
     * 슬라이딩 윈도우 Lua 스크립트: ZREMRANGEBYSCORE + ZADD + ZCARD + EXPIRE 원자 실행.
     * 외부 상태 없이 Redis 단일 스크립트로 race-free count.
     */
    private static final DefaultRedisScript<Long> SLIDING_WINDOW_SCRIPT =
        new DefaultRedisScript<>("""
            local key    = KEYS[1]
            local now    = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit  = tonumber(ARGV[3])
            local uid    = ARGV[4]
            redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)
            redis.call('ZADD', key, now, uid)
            local count = redis.call('ZCARD', key)
            redis.call('PEXPIRE', key, window)
            return count
            """, Long.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String userId = request.getHeader("X-User-Id");
        if (userId == null) {
            chain.doFilter(request, response);
            return;
        }

        String key = "rate:" + userId;
        long now   = System.currentTimeMillis();
        String uid = String.valueOf(System.nanoTime());

        Long count = stringRedisTemplate.execute(
            SLIDING_WINDOW_SCRIPT,
            List.of(key),
            String.valueOf(now),
            String.valueOf(windowMillis),
            String.valueOf(maxRequests),
            uid
        );

        if (count != null && count > maxRequests) {
            log.warn("Rate limit exceeded for userId={}", userId);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"success\":false,\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해주세요.\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
