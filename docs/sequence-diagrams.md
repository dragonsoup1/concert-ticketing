# 시퀀스 다이어그램

## 1. 대기열 토큰 발급 및 배치 입장

```mermaid
sequenceDiagram
    actor Client
    participant API as QueueController
    participant QS as QueueService
    participant Redis

    Client->>API: POST /api/queue/token?userId=42&concertId=1
    API->>QS: issueToken(userId, concertId)
    QS->>Redis: ZADD waiting:1 NX score=now userId=42
    note over Redis: 중복 발급 방지 (NX = addIfAbsent)
    Redis-->>QS: OK
    QS->>Redis: ZRANK waiting:1 42
    Redis-->>QS: rank=149
    QS-->>API: TokenIssueResponse(rank=149, estimatedWait=3s)
    API-->>Client: 200 OK

    Note over API,Redis: 1초마다 QueueScheduler.admitNextBatch() 실행

    participant Scheduler as QueueScheduler
    Scheduler->>QS: admitBatch(concertId=1)
    QS->>Redis: ZPOPMIN waiting:1 COUNT 50
    Redis-->>QS: [userId=1, userId=2, ..., userId=50]
    loop 입장 허가된 사용자마다
        QS->>Redis: SET admitted:userId:1 "1" PX 600000
        note over Redis: TTL 10분
    end
```

---

## 2. 좌석 예약 (Double-Lock + Outbox)

```mermaid
sequenceDiagram
    actor Client
    participant RateLimit as RateLimitFilter
    participant QueueFilter as QueueTokenFilter
    participant Controller as ReservationController
    participant Service as ReservationService
    participant Redisson
    participant DB as PostgreSQL
    participant OutboxWriter

    Client->>RateLimit: POST /api/reservations\n(X-User-Id, Idempotency-Key)
    RateLimit->>RateLimit: Lua: ZREMRANGEBYSCORE+ZADD+ZCARD+PEXPIRE\n(rate:{userId})
    alt count > 10
        RateLimit-->>Client: 429 Too Many Requests
    end

    RateLimit->>QueueFilter: pass
    QueueFilter->>QueueFilter: assertAdmitted(userId, concertId)
    alt admitted:{userId}:{concertId} 없음
        QueueFilter-->>Client: 400 QueueNotAdmittedException
    end

    QueueFilter->>Controller: pass
    Controller->>Service: createReservation(userId, seatId, concertId, idempotencyKey)

    Service->>Redisson: tryLock("seat:lock:{seatId}", wait=5s, lease=3s)
    alt 락 획득 실패
        Redisson-->>Client: 409 LockAcquisitionFailedException
    end
    Redisson-->>Service: lock acquired

    Service->>DB: SELECT user WHERE id=userId
    DB-->>Service: User

    Service->>DB: SELECT seat WHERE id=seatId (with @Version)
    DB-->>Service: Seat(status=AVAILABLE, version=3)

    Service->>DB: UPDATE seat SET status=HELD, version=4\n(Optimistic Lock: WHERE version=3)
    alt version 불일치 (동시 요청)
        DB-->>Client: 409 OptimisticLockException
    end

    Service->>DB: INSERT INTO reservations\n(status=PENDING, expiresAt=now+5min)
    Service->>OutboxWriter: write("Reservation", id, "ReservationCreated", payload)
    note over OutboxWriter: Propagation.MANDATORY — 동일 트랜잭션 내
    OutboxWriter->>DB: INSERT INTO outbox_events (status=PENDING)

    DB-->>Service: COMMIT
    Service->>Redisson: unlock("seat:lock:{seatId}")
    Service->>Service: evictSeatCache(concertId)
    Service-->>Client: 201 ReservationResponse(status=PENDING)
```

---

## 3. 결제 처리 (Idempotency + Circuit Breaker)

```mermaid
sequenceDiagram
    actor Client
    participant Interceptor as IdempotencyInterceptor
    participant Controller as PaymentController
    participant Service as PaymentService
    participant Redis
    participant DB as PostgreSQL
    participant PG as PaymentGatewayClient
    participant OutboxWriter

    Client->>Interceptor: POST /api/payments\n(Idempotency-Key: {key})
    Interceptor->>Redis: GET idempotency:{key}
    
    alt 캐시 히트 (중복 요청)
        Redis-->>Interceptor: cached PaymentResponse
        Interceptor-->>Client: 200 OK\n(Idempotent-Replayed: true)
    end

    Redis-->>Interceptor: null (캐시 미스)
    Interceptor->>Controller: pass

    Controller->>Service: processPayment(request, idempotencyKey)
    Service->>DB: SELECT payment WHERE idempotencyKey={key}
    alt 이미 처리됨 (cold-start fallback)
        DB-->>Client: 200 AlreadyProcessedPaymentException
    end

    Service->>DB: SELECT reservation WHERE id=reservationId (join fetch)
    DB-->>Service: Reservation

    Service->>PG: charge(PgChargeRequest) [Circuit Breaker + Retry + TimeLimiter 3s]
    
    alt Circuit Breaker OPEN
        PG-->>Service: chargeFallback() → PENDING_CONFIRMATION
    else PG 응답 성공
        PG-->>Service: PgResponse(success=true, txId="pg-txn-abc")
    else PG 응답 실패
        PG-->>Service: PgResponse(success=false)
    end

    Service->>DB: INSERT INTO payments (status, idempotencyKey, pgTransactionId)

    alt status == SUCCESS
        Service->>DB: UPDATE reservation SET status=CONFIRMED
        Service->>DB: UPDATE seat SET status=SOLD
        Service->>OutboxWriter: write("Payment", id, "PaymentCompleted", payload)
        OutboxWriter->>DB: INSERT INTO outbox_events (status=PENDING)
    end

    DB-->>Service: COMMIT
    Service->>Redis: SET idempotency:{key} PaymentResponse PX 86400000
    note over Redis: TTL 24시간

    Service-->>Client: 200 PaymentResponse
```

---

## 4. Outbox 릴레이 (Kafka 이벤트 발행)

```mermaid
sequenceDiagram
    participant Scheduler as OutboxRelayScheduler
    participant DB as PostgreSQL
    participant Kafka

    loop 500ms마다
        Scheduler->>DB: SELECT TOP 50 FROM outbox_events\nWHERE status=PENDING\nORDER BY created_at ASC
        DB-->>Scheduler: [OutboxEvent, ...]

        loop 각 이벤트
            Scheduler->>Kafka: send(topic=eventType, payload)
            
            alt 발행 성공
                Kafka-->>Scheduler: OK
                Scheduler->>DB: UPDATE outbox_events\nSET status=PUBLISHED, published_at=now
            else 발행 실패
                Kafka-->>Scheduler: Exception
                Scheduler->>DB: UPDATE outbox_events\nSET retry_count = retry_count + 1
                alt retry_count >= 5
                    Scheduler->>DB: UPDATE outbox_events\nSET status=FAILED
                    note over DB: 더 이상 재시도 안 함
                end
            end
        end
    end
```

---

## 5. 예약 만료 스케줄러 (30초 주기)

```mermaid
sequenceDiagram
    participant Scheduler as ReservationService\n(expireReservations)
    participant DB as PostgreSQL
    participant OutboxWriter

    loop 30초마다
        Scheduler->>DB: SELECT reservations\nWHERE status=PENDING AND expires_at < now()
        DB-->>Scheduler: [만료된 Reservation, ...]

        loop 각 만료 예약
            Scheduler->>DB: UPDATE reservation SET status=EXPIRED
            Scheduler->>DB: UPDATE seat SET status=AVAILABLE
            Scheduler->>OutboxWriter: write("Reservation", id, "ReservationExpired", payload)
            OutboxWriter->>DB: INSERT INTO outbox_events (status=PENDING)
        end

        DB-->>Scheduler: COMMIT
    end
```
