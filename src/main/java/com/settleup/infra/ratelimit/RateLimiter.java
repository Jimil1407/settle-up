package com.settleup.infra.ratelimit;

import java.time.Duration;

/** Fixed-window rate limiting, used to stop invite spam. */
public interface RateLimiter {

    /**
     * @return {@code true} if the action is allowed, {@code false} if the caller is over the limit
     */
    boolean tryConsume(String key, int limit, Duration window);
}
