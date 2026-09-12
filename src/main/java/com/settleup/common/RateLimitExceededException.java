package com.settleup.common;

/** 429 - too many requests from this caller for the given action. */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
