package com.settleup.infra.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Fixed-window counter evaluated inside Redis as a Lua script.
 *
 * <p>The script matters. Doing {@code INCR} and {@code EXPIRE} as two round trips is a real bug:
 * if the process dies between them the key never expires and the caller is locked out forever, and
 * two concurrent callers can both observe {@code current == 1} and both reset the window. Lua runs
 * the whole check-and-expire atomically in a single round trip.
 */
@Component
@ConditionalOnProperty(name = "settleup.redis-enabled", havingValue = "true")
@Slf4j
public class RedisRateLimiter implements RateLimiter {

    private static final String LUA = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            if current > tonumber(ARGV[1]) then
              return 0
            end
            return 1
            """;

    private final StringRedisTemplate redis;
    private final RedisScript<Long> script;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
        this.script = new DefaultRedisScript<>(LUA, Long.class);
    }

    @Override
    public boolean tryConsume(String key, int limit, Duration window) {
        try {
            Long allowed = redis.execute(
                    script,
                    List.of("settleup:rl:" + key),
                    String.valueOf(limit),
                    String.valueOf(window.toMillis()));
            return allowed == null || allowed == 1L;
        } catch (RuntimeException ex) {
            // Fail open: a rate limiter outage should not take the product down.
            log.warn("rate limit check failed for key {}, allowing request: {}", key, ex.toString());
            return true;
        }
    }
}
