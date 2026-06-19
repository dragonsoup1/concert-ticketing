package com.api.backend.reservation.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT r FROM Reservation r WHERE r.status = 'PENDING' AND r.expiresAt < :now")
    List<Reservation> findExpired(LocalDateTime now);

    @Query("SELECT r FROM Reservation r JOIN FETCH r.seat s JOIN FETCH s.concert WHERE r.id = :id")
    Optional<Reservation> findByIdWithDetails(Long id);
}
