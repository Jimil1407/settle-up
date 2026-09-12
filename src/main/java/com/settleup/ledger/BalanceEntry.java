package com.settleup.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * One line of the append-only balance ledger.
 *
 * <p>Balances are never stored as a mutable running total. Instead every expense and every
 * settlement appends a set of signed deltas that sum to zero, and a user's balance is
 * {@code SUM(delta_paise)} over their entries. This buys three things for almost no complexity:
 * <ul>
 *   <li><b>No lost updates.</b> Inserts don't read-modify-write a total, so nothing can be
 *       clobbered by a concurrent writer.</li>
 *   <li><b>A free audit trail.</b> Every paisa is traceable to the expense or settlement that
 *       caused it, because rows are only ever inserted, never updated or deleted.</li>
 *   <li><b>A testable invariant.</b> The entries for any group must sum to exactly zero. That
 *       single assertion catches almost every class of money bug.</li>
 * </ul>
 *
 * <p>Sign convention: <b>positive means the group owes this user</b> (they are out of pocket),
 * negative means this user owes the group.
 */
@Entity
@Table(name = "balance_entry")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Signed amount in paise. Entries written in one transaction always sum to zero. */
    @Column(name = "delta_paise", nullable = false)
    private long deltaPaise;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private LedgerSource sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
