package com.settleup.infra.idempotency;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory stand-in used by tests and by {@code docker-compose}-less local runs, so the whole
 * suite can execute without a Redis container. Single-JVM only, which is exactly why production
 * uses the Redis implementation.
 */
@Component
@ConditionalOnProperty(name = "settleup.redis-enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private final Map<String, Instant> keys = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key, Duration ttl) {
        Instant now = Instant.now();
        keys.values().removeIf(expiry -> expiry.isBefore(now));
        return keys.putIfAbsent(key, now.plus(ttl)) == null;
    }

    @Override
    public void release(String key) {
        keys.remove(key);
    }
}
