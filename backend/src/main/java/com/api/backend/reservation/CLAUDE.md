# Reservation Domain

## Double-lock on seat reservation (`ReservationService.createReservation`)
- Redisson distributed lock: prevents concurrent entry across JVM nodes
- JPA `@Version` optimistic lock: last-resort guard if the Redis lock expires unexpectedly
- Both are intentional — removing either weakens the safety guarantee

## State Machine
```
PENDING ──(5-min TTL)──► EXPIRED  (seat released)
   │
   ├──(POST /payments success)──► CONFIRMED ──(PG webhook)──► PAID
   └──(DELETE /reservations)──► CANCELLED   (seat released)
```
