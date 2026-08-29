package com.sogeco.fleet.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String resource, Object identifier) {
        super("RESOURCE_NOT_FOUND",
              "%s introuvable : %s".formatted(resource, identifier),
              HttpStatus.NOT_FOUND);
    }
}
