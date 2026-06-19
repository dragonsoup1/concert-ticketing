# Payment Domain

## Idempotency dual guard
- Redis `idempotency:{key}` (24h TTL): fast path, handled by `IdempotencyInterceptor` before service layer
- `Payment.idempotencyKey` unique DB constraint: cold-start / Redis eviction fallback
