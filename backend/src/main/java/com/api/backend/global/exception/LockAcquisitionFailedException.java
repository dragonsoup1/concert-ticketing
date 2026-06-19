package com.api.backend.global.exception;

import org.springframework.http.HttpStatus;

public class LockAcquisitionFailedException extends BusinessException {

    public LockAcquisitionFailedException(String key) {
        super("요청이 너무 많습니다. 잠시 후 다시 시도해주세요. key=" + key, HttpStatus.TOO_MANY_REQUESTS);
    }
}
