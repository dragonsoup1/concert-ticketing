# API 명세서

## 공통

### Base URL

```
http://localhost:8080
```

### 공통 헤더

| 헤더 | 필수 | 설명 |
|------|------|------|
| `X-User-Id` | 모든 요청 | 사용자 ID (Long). Rate Limit 기준 키 |
| `Idempotency-Key` | POST /reservations, POST /payments | 멱등성 키. 동일 키로 중복 요청 시 최초 결과 반환 |

### 공통 응답 포맷

```json
{
  "success": true,
  "data": { ... },
  "error": null
}
```

```json
{
  "success": false,
  "data": null,
  "error": "에러 메시지"
}
```

### 공통 에러 코드

| HTTP | 설명 |
|------|------|
| 400 | 유효성 실패 또는 Idempotency-Key 누락 |
| 404 | 리소스 없음 |
| 409 | 락 획득 실패 또는 좌석 선점 충돌 |
| 429 | Rate Limit 초과 (10 req/s) |
| 500 | 서버 내부 오류 |

---

## 콘서트

### GET /api/concerts

예정된 콘서트 목록을 페이지네이션으로 조회한다.

**Query Parameters**

| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|--------|------|
| page | int | 0 | 페이지 번호 |
| size | int | 20 | 페이지 크기 |
| sort | string | - | 정렬 기준 (e.g. `eventAt,asc`) |

**Response 200**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "title": "2025 아이유 콘서트",
        "venue": "올림픽공원 체조경기장",
        "eventAt": "2025-09-15T19:00:00",
        "totalSeats": 5000
      }
    ],
    "totalElements": 42,
    "totalPages": 3,
    "number": 0,
    "size": 20
  },
  "error": null
}
```

---

### GET /api/concerts/{concertId}

콘서트 상세 정보를 조회한다. Redis에 5분간 캐시된다.

**Path Parameters**

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| concertId | Long | 콘서트 ID |

**Response 200**

```json
{
  "success": true,
  "data": {
    "id": 1,
    "title": "2025 아이유 콘서트",
    "venue": "올림픽공원 체조경기장",
    "eventAt": "2025-09-15T19:00:00",
    "totalSeats": 5000
  },
  "error": null
}
```

**Error**

| HTTP | 조건 |
|------|------|
| 404 | 해당 concertId가 존재하지 않음 |

---

### GET /api/concerts/{concertId}/seats

콘서트의 잔여 좌석 목록을 조회한다. Redis에 60초간 캐시된다.

**Path Parameters**

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| concertId | Long | 콘서트 ID |

**Response 200**

```json
{
  "success": true,
  "data": [
    {
      "id": 101,
      "seatNumber": "A-01",
      "grade": "VIP",
      "price": 150000,
      "status": "AVAILABLE"
    },
    {
      "id": 102,
      "seatNumber": "B-01",
      "grade": "GENERAL",
      "price": 99000,
      "status": "HELD"
    }
  ],
  "error": null
}
```

**SeatGrade**: `VIP` | `GENERAL`

**SeatStatus**: `AVAILABLE` | `HELD` | `SOLD`

---

## 대기열

### POST /api/queue/token

콘서트 대기열에 입장 토큰을 발급한다. 이미 대기 중인 경우 현재 순위를 반환한다.

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| userId | Long | Y | 사용자 ID |
| concertId | Long | Y | 콘서트 ID |

**Response 200**

```json
{
  "success": true,
  "data": {
    "userId": 42,
    "concertId": 1,
    "rank": 150,
    "estimatedWaitSeconds": 3
  },
  "error": null
}
```

- `rank`: 대기열 내 순위 (0-based)
- `estimatedWaitSeconds`: 예상 대기 시간 (`(rank / batchSize + 1)` 초)

---

### GET /api/queue/status

현재 대기 상태 또는 입장 허가 여부를 조회한다.

**Query Parameters**

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| userId | Long | Y | 사용자 ID |
| concertId | Long | Y | 콘서트 ID |

**Response 200 — 입장 허가됨**

```json
{
  "success": true,
  "data": {
    "admitted": true,
    "rank": null,
    "estimatedWaitSeconds": 0
  },
  "error": null
}
```

**Response 200 — 대기 중**

```json
{
  "success": true,
  "data": {
    "admitted": false,
    "rank": 80,
    "estimatedWaitSeconds": 2
  },
  "error": null
}
```

---

## 예약

### POST /api/reservations

입장 허가된 사용자의 좌석을 임시 선점한다. 5분 내 결제하지 않으면 자동 만료된다.

**Headers**

| 헤더 | 필수 | 설명 |
|------|------|------|
| `Idempotency-Key` | Y | 클라이언트 생성 UUID |

**Request Body**

```json
{
  "userId": 42,
  "seatId": 101,
  "concertId": 1
}
```

| 필드 | 타입 | 제약 |
|------|------|------|
| userId | Long | NotNull |
| seatId | Long | NotNull |
| concertId | Long | NotNull |

**Response 201**

```json
{
  "success": true,
  "data": {
    "id": 500,
    "userId": 42,
    "seatId": 101,
    "status": "PENDING",
    "expiresAt": "2025-06-19T14:35:00"
  },
  "error": null
}
```

**Error**

| HTTP | 조건 |
|------|------|
| 400 | 입장 허가 없음 (`QueueNotAdmittedException`) |
| 400 | Idempotency-Key 헤더 누락 |
| 404 | userId 또는 seatId 없음 |
| 409 | 좌석이 AVAILABLE 상태가 아님 (이미 선점됨) |
| 409 | 분산 락 획득 실패 (동시 요청 충돌) |

---

### DELETE /api/reservations/{reservationId}

예약을 취소하고 좌석을 반환한다.

**Path Parameters**

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| reservationId | Long | 예약 ID |

**Response 200**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**Error**

| HTTP | 조건 |
|------|------|
| 404 | 해당 reservationId 없음 |

---

## 결제

### POST /api/payments

예약에 대한 결제를 처리한다. PG사 장애 시 Circuit Breaker가 작동해 `PENDING_CONFIRMATION` 상태로 기록된다.

**Headers**

| 헤더 | 필수 | 설명 |
|------|------|------|
| `Idempotency-Key` | Y | 클라이언트 생성 UUID. 동일 키 재요청 시 `Idempotent-Replayed: true` 헤더와 함께 최초 결과 반환 |

**Request Body**

```json
{
  "reservationId": 500,
  "amount": 150000
}
```

| 필드 | 타입 | 제약 |
|------|------|------|
| reservationId | Long | NotNull |
| amount | BigDecimal | NotNull, Positive |

**Response 200**

```json
{
  "success": true,
  "data": {
    "id": 300,
    "reservationId": 500,
    "amount": 150000,
    "status": "SUCCESS",
    "pgTransactionId": "pg-txn-abc123"
  },
  "error": null
}
```

**PaymentStatus**: `REQUESTED` | `SUCCESS` | `FAILED` | `PENDING_CONFIRMATION`

**Response Headers (중복 요청)**

```
Idempotent-Replayed: true
```

**Error**

| HTTP | 조건 |
|------|------|
| 400 | Idempotency-Key 헤더 누락 |
| 404 | reservationId 없음 |
