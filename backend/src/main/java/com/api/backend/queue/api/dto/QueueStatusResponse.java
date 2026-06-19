package com.api.backend.queue.api.dto;

public record QueueStatusResponse(
    boolean admitted,
    Long rank,
    long estimatedWaitSeconds
) {
    public static QueueStatusResponse ofAdmitted() {
        return new QueueStatusResponse(true, null, 0);
    }

    public static QueueStatusResponse ofWaiting(long rank, long batchSize) {
        long estimatedWaitSeconds = (rank / batchSize) + 1;
        return new QueueStatusResponse(false, rank, estimatedWaitSeconds);
    }
}
