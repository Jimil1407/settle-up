package com.settleup.infra.cache;

import java.util.Map;
import java.util.Optional;

/**
 * Caches a group's computed net balances.
 *
 * <p>Cache entries are keyed by {@code (groupId, version)} rather than by group alone. Because the
 * group's version is bumped by the same locked transaction that writes ledger entries, a balance
 * change automatically produces a new cache key and the stale entry becomes unreachable. That
 * removes the classic cache-invalidation race where a writer deletes the key a fraction before a
 * slow reader puts a stale value back. Old keys simply age out via TTL.
 */
public interface BalanceCache {

    Optional<Map<Long, Long>> get(Long groupId, long version);

    void put(Long groupId, long version, Map<Long, Long> balances);
}
