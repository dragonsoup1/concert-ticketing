package com.api.backend.concert.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface ConcertRepository extends JpaRepository<Concert, Long> {
    Page<Concert> findByEventAtAfterOrderByEventAtAsc(LocalDateTime after, Pageable pageable);
}
