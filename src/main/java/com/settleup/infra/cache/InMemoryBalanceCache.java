package com.settleup.infra.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded in-memory cache used when Redis is not enabled. */
@Component
@ConditionalOnProperty(name = "settleup.redis-enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryBalanceCache implements BalanceCache {

    private static final int MAX_ENTRIES = 1_000;

    private final Map<String, Map<Long, Long>> cache = new ConcurrentHashMap<>();

    private static String key(Long groupId, long version) {
        return groupId + ":v" + version;
    }

    @Override
    public Optional<Map<Long, Long>> get(Long groupId, long version) {
        return Optional.ofNullable(cache.get(key(groupId, version))).map(LinkedHashMap::new);
    }

    @Override
    public void put(Long groupId, long version, Map<Long, Long> balances) {
        // Version-keyed entries accumulate as groups change, so evict crudely rather than leak.
        if (cache.size() >= MAX_ENTRIES) {
            cache.clear();
        }
        cache.put(key(groupId, version), new LinkedHashMap<>(balances));
    }
}
