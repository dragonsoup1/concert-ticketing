package com.api.backend.concert.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    @Query("SELECT s FROM Seat s WHERE s.concert.id = :concertId AND s.status = 'AVAILABLE'")
    List<Seat> findAvailableByConcertId(Long concertId);

    @Query("SELECT s FROM Seat s JOIN FETCH s.concert WHERE s.id = :id")
    Optional<Seat> findByIdWithConcert(Long id);
}
