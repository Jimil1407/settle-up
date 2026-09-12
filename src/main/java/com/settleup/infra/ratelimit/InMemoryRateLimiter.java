package com.settleup.infra.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** In-memory fixed-window limiter used when Redis is not enabled. */
@Component
@ConditionalOnProperty(name = "settleup.redis-enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryRateLimiter implements RateLimiter {

    private record Window(Instant expiresAt, AtomicLong count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public boolean tryConsume(String key, int limit, Duration window) {
        Instant now = Instant.now();
        Window w = windows.compute(key, (k, existing) ->
                (existing == null || existing.expiresAt().isBefore(now))
                        ? new Window(now.plus(window), new AtomicLong(0))
                        : existing);
        return w.count().incrementAndGet() <= limit;
    }
}
