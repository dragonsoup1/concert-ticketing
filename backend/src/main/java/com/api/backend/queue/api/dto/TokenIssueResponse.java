package com.api.backend.queue.api.dto;

public record TokenIssueResponse(
    Long userId,
    Long concertId,
    long rank,
    long estimatedWaitSeconds
) {}
