package com.sogeco.fleet.common.exception;

import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends BusinessException {

    public DuplicateResourceException(String resource, String field, Object value) {
        super("DUPLICATE_RESOURCE",
              "%s existe deja avec %s = %s".formatted(resource, field, value),
              HttpStatus.CONFLICT);
    }
}
