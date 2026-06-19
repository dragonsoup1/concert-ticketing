package com.api.backend.payment.application.dto;

public record PgResponse(
    boolean success,
    String transactionId,
    boolean pending
) {
    public static PgResponse success(String transactionId) {
        return new PgResponse(true, transactionId, false);
    }

    public static PgResponse pendingFallback() {
        return new PgResponse(false, null, true);
    }
}
