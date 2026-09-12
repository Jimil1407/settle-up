package com.settleup.group;

import com.settleup.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single place that defines how a balance-changing write acquires its lock.
 *
 * <p>Centralising this matters for more than tidiness: every writer taking the same lock on the
 * same row in the same way is what makes the protocol provably deadlock-free, and it means the
 * "how do we stay consistent?" question has exactly one answer to audit.
 *
 * <h2>The protocol</h2>
 * <ol>
 *   <li>{@link #lockForBalanceWrite} takes a {@code PESSIMISTIC_WRITE} lock
 *       ({@code SELECT ... FOR UPDATE}) on the group row, serialising this transaction against
 *       every other balance write for the same group, and leaves the version untouched so callers
 *       read its true current value;</li>
 *   <li>the caller appends a balanced set of ledger entries;</li>
 *   <li>{@link #markBalanceChanged} advances the version, invalidating the version-keyed balance
 *       cache and any settlement plan a client is still holding.</li>
 * </ol>
 *
 * <h2>Why the version bump is an explicit UPDATE</h2>
 * The idiomatic-looking approach, {@code em.lock(group, OPTIMISTIC_FORCE_INCREMENT)}, is a trap:
 * Hibernate orders lock modes and will not downgrade, so an entity already held at
 * {@code PESSIMISTIC_WRITE} silently discards the weaker force-increment request and the version
 * never changes. Nothing throws; balances simply stop invalidating their cache. The concurrency
 * test asserts that the version advances exactly once per write, precisely so this cannot regress.
 */
@Service
@RequiredArgsConstructor
public class GroupLockService {

    private final ExpenseGroupRepository groupRepository;

    /**
     * Serialises this transaction against every other balance write for the same group.
     *
     * <p>Must be called inside an existing transaction; a lock released the instant the method
     * returns would protect nothing, so we fail loudly rather than pretend.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ExpenseGroup lockForBalanceWrite(Long groupId) {
        return groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new NotFoundException("group " + groupId + " not found"));
    }

    /**
     * Marks the group's balances as changed. Safe because the caller holds the row lock, so the
     * read-modify-write inside the {@code UPDATE} cannot race another writer.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void markBalanceChanged(Long groupId) {
        groupRepository.incrementVersion(groupId);
    }
}
