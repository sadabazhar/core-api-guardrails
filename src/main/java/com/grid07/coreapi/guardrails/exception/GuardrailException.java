package com.grid07.coreapi.guardrails.exception;

import org.springframework.http.HttpStatus;

public class GuardrailException extends BaseException {
    public GuardrailException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
