# Global Infrastructure

## Redis Key Namespace

| Key pattern | Type | TTL | Purpose |
|---|---|---|---|
| `waiting:{concertId}` | ZSet | — | Queue (score = enqueue epoch ms) |
| `admitted:{userId}:{concertId}` | String | 10 min | Admission pass |
| `seat:lock:{seatId}` | Redisson RLock | 3 s | Distributed lock |
| `rate:{userId}` | ZSet | 1 s | Rate-limit sliding window |
| `idempotency:{key}` | String | 24 h | Payment idempotency cache |
| `concert:{id}` | String | 5 min | Concert detail cache |
| `seats:{concertId}` | String | 60 s | Available seat list cache |

## Rate limiting (`RateLimitFilter`)
- Single Lua script: `ZREMRANGEBYSCORE + ZADD + ZCARD + PEXPIRE` — no TOCTOU race
- 1-second sliding window, 10 req/user, HTTP 429 on exceed
