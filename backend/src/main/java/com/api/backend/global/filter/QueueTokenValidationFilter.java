package com.api.backend.global.filter;

import com.api.backend.queue.application.QueueService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class QueueTokenValidationFilter extends OncePerRequestFilter {

    private final QueueService queueService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (shouldValidate(request)) {
            String userIdHeader    = request.getHeader("X-User-Id");
            String concertIdHeader = request.getHeader("X-Concert-Id");

            if (userIdHeader != null && concertIdHeader != null) {
                queueService.assertAdmitted(
                    Long.parseLong(userIdHeader),
                    Long.parseLong(concertIdHeader));
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean shouldValidate(HttpServletRequest request) {
        String uri    = request.getRequestURI();
        String method = request.getMethod();
        return "POST".equals(method) && uri.startsWith("/api/reservations");
    }
}
