package com.settleup.infra.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "settleup.redis-enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class RedisBalanceCache implements BalanceCache {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final TypeReference<Map<Long, Long>> BALANCES = new TypeReference<>() {
    };

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    private static String key(Long groupId, long version) {
        return "settleup:balances:" + groupId + ":v" + version;
    }

    @Override
    public Optional<Map<Long, Long>> get(Long groupId, long version) {
        try {
            String json = redis.opsForValue().get(key(groupId, version));
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, BALANCES));
        } catch (Exception ex) {
            // A cache miss and a broken cache should behave identically: recompute from the ledger.
            log.warn("balance cache read failed for group {}: {}", groupId, ex.toString());
            return Optional.empty();
        }
    }

    @Override
    public void put(Long groupId, long version, Map<Long, Long> balances) {
        try {
            redis.opsForValue().set(key(groupId, version), objectMapper.writeValueAsString(balances), TTL);
        } catch (Exception ex) {
            log.warn("balance cache write failed for group {}: {}", groupId, ex.toString());
        }
    }
}
