package com.sogeco.fleet.common.exception;

import org.springframework.http.HttpStatus;

public class AccessDeniedBusinessException extends BusinessException {

    public AccessDeniedBusinessException(String message) {
        super("ACCESS_DENIED", message, HttpStatus.FORBIDDEN);
    }
}
