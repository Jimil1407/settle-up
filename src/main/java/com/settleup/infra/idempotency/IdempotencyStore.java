package com.settleup.infra.idempotency;

import java.time.Duration;

/**
 * Guards against duplicate side effects from retried requests.
 *
 * <p>This is only the <b>fast path</b>. The durable guarantee lives in the database as a unique
 * index on {@code idempotency_key}. That split matters: Redis can be flushed, evicted or briefly
 * unavailable, so treating it as the source of truth for "have I already charged this?" would be a
 * correctness bug. Here it is a cheap filter that stops the common case early, while the unique
 * constraint is what actually makes double-charging impossible.
 */
public interface IdempotencyStore {

    /**
     * @return {@code true} if this caller now owns the key, {@code false} if it was already taken
     */
    boolean tryAcquire(String key, Duration ttl);

    /** Releases a key so a failed attempt can be retried immediately rather than after the TTL. */
    void release(String key);
}
