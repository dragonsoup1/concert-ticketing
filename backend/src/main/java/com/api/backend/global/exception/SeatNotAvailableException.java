package com.api.backend.global.exception;

import org.springframework.http.HttpStatus;

public class SeatNotAvailableException extends BusinessException {

    public SeatNotAvailableException() {
        super("이미 선택된 좌석입니다.", HttpStatus.CONFLICT);
    }
}
