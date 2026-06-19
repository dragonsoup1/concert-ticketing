package com.api.backend.queue.api;

import com.api.backend.global.response.ApiResponse;
import com.api.backend.queue.api.dto.QueueStatusResponse;
import com.api.backend.queue.api.dto.TokenIssueResponse;
import com.api.backend.queue.application.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    @PostMapping("/token")
    public ApiResponse<TokenIssueResponse> issueToken(
        @RequestParam Long userId,
        @RequestParam Long concertId) {
        return ApiResponse.ok(queueService.issueToken(userId, concertId));
    }

    @GetMapping("/status")
    public ApiResponse<QueueStatusResponse> getStatus(
        @RequestParam Long userId,
        @RequestParam Long concertId) {
        return ApiResponse.ok(queueService.getStatus(userId, concertId));
    }
}
