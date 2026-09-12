package com.settleup.infra.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Redis-backed idempotency using {@code SET key value NX PX ttl}, which is atomic. */
@Component
@ConditionalOnProperty(name = "settleup.redis-enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final String PREFIX = "settleup:idem:";

    private final StringRedisTemplate redis;

    @Override
    public boolean tryAcquire(String key, Duration ttl) {
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent(PREFIX + key, "1", ttl);
            return Boolean.TRUE.equals(acquired);
        } catch (RuntimeException ex) {
            // Redis is an optimisation, not the source of truth. If it is down we let the request
            // through and rely on the unique index to reject an actual duplicate.
            log.warn("idempotency check failed for key {}, falling through to the database: {}",
                    key, ex.toString());
            return true;
        }
    }

    @Override
    public void release(String key) {
        try {
            redis.delete(PREFIX + key);
        } catch (RuntimeException ex) {
            log.warn("failed to release idempotency key {}: {}", key, ex.toString());
        }
    }
}
