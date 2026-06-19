package com.api.backend.global.exception;

import org.springframework.http.HttpStatus;

public class MissingIdempotencyKeyException extends BusinessException {

    public MissingIdempotencyKeyException() {
        super("Idempotency-Key 헤더가 필요합니다.", HttpStatus.BAD_REQUEST);
    }
}
