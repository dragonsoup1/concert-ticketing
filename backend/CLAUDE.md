# CLAUDE.md

## Commands

```bash
# Run (devtools provides auto-restart)
gradlew.bat bootRun

# Build
gradlew.bat build

# Run all tests
gradlew.bat test

# Run a single test class
gradlew.bat test --tests "com.api.backend.reservation.application.ReservationServiceTest"
```

Requires PostgreSQL (5432), Redis (6379), and Kafka (9092) — start with `docker compose up -d` from the repo root.

## Architecture

Domain-driven package structure under `com.api.backend`:

| Package | Responsibility | Key pattern | Details |
|---|---|---|---|
| `concert` | Concert & Seat read APIs | `@Cacheable` / `@CacheEvict` (Redis, TTL 5min/60s) | — |
| `queue` | Virtual waiting queue | Redis Sorted Set; `@Scheduled` batch admission | — |
| `reservation` | Seat booking + state machine | Distributed lock (Redisson) + Optimistic lock (`@Version`) | reservation/CLAUDE.md |
| `payment` | Payment processing | Idempotency-Key header + Resilience4j Circuit Breaker | payment/CLAUDE.md |
| `outbox` | Reliable event publishing | Transactional Outbox → Kafka | outbox/CLAUDE.md |
| `global/lock` | `@DistributedLock` AOP | Redisson `RLock` + SpEL key resolution | global/CLAUDE.md |
| `global/filter` | `RateLimitFilter` | Redis sliding window via Lua script (atomic) | global/CLAUDE.md |
| `global/interceptor` | `IdempotencyInterceptor` | Redis cache + DB unique key dual guard | global/CLAUDE.md |
| `global/exception` | `GlobalExceptionHandler` | `@RestControllerAdvice` → `ApiResponse` envelope | — |
