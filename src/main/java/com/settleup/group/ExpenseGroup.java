package com.settleup.group;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A shared expense group (flatmates, a trip, a dinner club).
 *
 * <p>The group row doubles as the <b>concurrency control point</b> for the whole group. Every
 * operation that changes balances first takes a {@code PESSIMISTIC_WRITE} lock on this row
 * ({@code SELECT ... FOR UPDATE}), so concurrent balance writes for one group are serialised
 * instead of interleaving and losing each other's updates.
 *
 * <p>Locking at group granularity is deliberate: groups are small and low-traffic, so the
 * contention cost is negligible, and in exchange every balance mutation for a group is trivially
 * serialised and easy to reason about.
 */
@Entity
@Table(name = "expense_group")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    /**
     * Incremented by every balance-changing write. Clients echo it back when applying a settlement
     * plan so we can reject plans computed against balances that have since moved, and it also
     * keys the balance cache.
     *
     * <p><b>Deliberately not a JPA {@code @Version} field.</b> The obvious implementation — take
     * the row lock, then call {@code em.lock(group, OPTIMISTIC_FORCE_INCREMENT)} — looks correct
     * and silently does nothing. Hibernate treats lock modes as a hierarchy and refuses to
     * "downgrade", so once the entity is held at {@code PESSIMISTIC_WRITE} the weaker
     * force-increment request is discarded and the version never moves. Dirty-checking a
     * {@code lastUpdated} timestamp instead is no better: two commits landing in the same clock
     * tick write an identical value, Hibernate sees no change, and the version silently stalls
     * again.
     *
     * <p>So this counter is advanced explicitly by
     * {@code ExpenseGroupRepository.incrementVersion}, under the row lock. Slightly less magical,
     * and it actually happens — which the concurrency test asserts directly.
     */
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
