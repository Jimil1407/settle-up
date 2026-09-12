package com.settleup.group;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ExpenseGroupRepository extends JpaRepository<ExpenseGroup, Long> {

    /**
     * Takes the group's write lock without touching its version.
     *
     * <p>Emits {@code SELECT ... FOR UPDATE}, so concurrent balance writers for the same group
     * queue up behind this lock instead of interleaving and losing each other's updates. Because
     * the version is left alone, callers read its true current value — which is what makes the
     * stale-plan comparison in the settlement flow meaningful.
     *
     * <p>Every code path that appends to the ledger must go through
     * {@code GroupLockService.lockForBalanceWrite} first. Since the lock is always taken on a
     * single, always-present row, lock ordering is trivially consistent and this cannot deadlock
     * against itself.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from ExpenseGroup g where g.id = :id")
    Optional<ExpenseGroup> findByIdForUpdate(@Param("id") Long id);

    /**
     * Advances the group's version by one.
     *
     * <p>Written as an explicit {@code UPDATE} rather than left to Hibernate's optimistic-locking
     * machinery, because that machinery silently does nothing here — see the note on
     * {@code ExpenseGroup.version}. Callers must already hold the row lock, which is what makes
     * this read-modify-write safe.
     */
    @Modifying(flushAutomatically = true)
    @Query("update ExpenseGroup g set g.version = g.version + 1 where g.id = :id")
    int incrementVersion(@Param("id") Long id);
}
