package com.api.backend.global.exception;

import org.springframework.http.HttpStatus;

public class QueueNotAdmittedException extends BusinessException {

    public QueueNotAdmittedException() {
        super("대기열 입장 허가가 필요합니다.", HttpStatus.FORBIDDEN);
    }
}
