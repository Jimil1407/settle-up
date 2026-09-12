package com.settleup.common;

/** 403 - the caller is authenticated but is not a member of the group they are poking at. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
