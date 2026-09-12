package com.settleup.common;

/** 404 - the entity does not exist, or the caller is not allowed to know that it does. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
