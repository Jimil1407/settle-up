package com.settleup.common;

/** 400 - the request was understood but breaks a business rule. */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
