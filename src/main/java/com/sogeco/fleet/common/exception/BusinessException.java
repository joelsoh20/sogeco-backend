package com.sogeco.fleet.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Violation d'une regle de gestion. Le code correspond a la reference
 * de la regle du cahier des charges fonctionnel (ex : "RG-5.3").
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, String message) {
        this(code, message, HttpStatus.CONFLICT);
    }

    public BusinessException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }
}
