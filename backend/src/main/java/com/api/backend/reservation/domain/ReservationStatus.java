package com.api.backend.reservation.domain;

public enum ReservationStatus {
    PENDING, CONFIRMED, PAID, CANCELLED, EXPIRED;

    public boolean canTransitionTo(ReservationStatus next) {
        return switch (this) {
            case PENDING   -> next == CONFIRMED || next == CANCELLED || next == EXPIRED;
            case CONFIRMED -> next == PAID || next == CANCELLED;
            default        -> false;
        };
    }
}
