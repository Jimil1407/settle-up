package com.settleup.ledger;

import com.settleup.common.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The only component allowed to write to the balance ledger.
 *
 * <p>Funnelling every write through one class is what makes the sum-to-zero invariant enforceable:
 * {@link #append} refuses to persist a batch of entries that does not net to zero, so a bug in an
 * expense split or a settlement is caught at the moment it would corrupt the ledger, instead of
 * being discovered weeks later as balances that quietly don't add up.
 */
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final BalanceEntryRepository balanceEntryRepository;

    /**
     * Appends a balanced batch of deltas.
     *
     * <p>Callers must already hold the group's write lock (see
     * {@code ExpenseGroupRepository.findByIdForUpdate}) and must be inside a transaction, so that
     * the entries and the version bump commit atomically.
     *
     * @param deltasByUser signed paise per user; must sum to exactly zero
     * @throws IllegalStateException if the batch is unbalanced
     */
    @Transactional
    public List<BalanceEntry> append(Long groupId,
                                     Map<Long, Long> deltasByUser,
                                     LedgerSource sourceType,
                                     Long sourceId) {
        long sum = deltasByUser.values().stream().mapToLong(Long::longValue).sum();
        if (sum != 0L) {
            throw new IllegalStateException(String.format(
                    "refusing to write an unbalanced ledger batch for group %d: deltas sum to %s, not zero",
                    groupId, Money.format(sum)));
        }

        Instant now = Instant.now();
        List<BalanceEntry> entries = new ArrayList<>(deltasByUser.size());
        deltasByUser.forEach((userId, delta) -> {
            // Zero deltas carry no information and would just bloat the ledger.
            if (delta != 0L) {
                entries.add(BalanceEntry.builder()
                        .groupId(groupId)
                        .userId(userId)
                        .deltaPaise(delta)
                        .sourceType(sourceType)
                        .sourceId(sourceId)
                        .createdAt(now)
                        .build());
            }
        });
        return balanceEntryRepository.saveAll(entries);
    }

    /**
     * Net balance per user for a group, aggregated from the ledger.
     *
     * @return positive = the group owes this user, negative = this user owes the group
     */
    @Transactional(readOnly = true)
    public Map<Long, Long> netBalances(Long groupId) {
        Map<Long, Long> balances = new LinkedHashMap<>();
        for (BalanceEntryRepository.BalanceProjection row : balanceEntryRepository.findNetBalances(groupId)) {
            balances.put(row.getUserId(), row.getBalance() == null ? 0L : row.getBalance());
        }
        return balances;
    }

    /**
     * Net balances for every member, including members who have no ledger entries yet so that a
     * freshly added member shows up as a zero balance rather than vanishing from the response.
     */
    @Transactional(readOnly = true)
    public Map<Long, Long> netBalancesForMembers(Long groupId, List<Long> memberIds) {
        Map<Long, Long> actual = netBalances(groupId);
        Map<Long, Long> result = new LinkedHashMap<>();
        for (Long memberId : memberIds) {
            result.put(memberId, actual.getOrDefault(memberId, 0L));
        }
        // A member who left the group can still hold a non-zero balance; dropping them would break
        // the sum-to-zero property of the map handed to the debt simplifier.
        actual.forEach(result::putIfAbsent);
        return result;
    }

    @Transactional(readOnly = true)
    public long balanceOf(Long groupId, Long userId) {
        return balanceEntryRepository.sumDeltaByGroupIdAndUserId(groupId, userId);
    }

    /**
     * Verifies the core invariant: a group's ledger entries sum to exactly zero. Exposed so tests
     * and the {@code /internal/ledger-check} endpoint can assert it after concurrent activity.
     */
    @Transactional(readOnly = true)
    public long imbalanceOf(Long groupId) {
        return balanceEntryRepository.sumDeltaByGroupId(groupId);
    }
}
