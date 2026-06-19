# Outbox Domain

## Outbox pattern (`OutboxWriter` + `OutboxRelayScheduler`)
- `OutboxWriter.write()` runs with `Propagation.MANDATORY` — enforces same-transaction insertion
- Scheduler polls every 500ms; max 5 retries before marking FAILED
- Guarantees at-least-once Kafka delivery without distributed transactions
