package com.settleup.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DebtSimplifierTest {

    @Test
    @DisplayName("the trip scenario: three tangled debts collapse into two payments")
    void simplifiesATypicalTrip() {
        // Aditi is owed 300, Rohan owes 100, Sana owes 200.
        Map<Long, Long> balances = new LinkedHashMap<>();
        balances.put(1L, 30_000L);
        balances.put(2L, -10_000L);
        balances.put(3L, -20_000L);

        List<DebtSimplifier.Transfer> transfers = DebtSimplifier.simplify(balances);

        assertThat(transfers).hasSize(2);
        assertThat(transfers).allSatisfy(t -> assertThat(t.toUserId()).isEqualTo(1L));
        assertThat(transfers.stream().mapToLong(DebtSimplifier.Transfer::amountPaise).sum())
                .isEqualTo(30_000L);
        assertSettles(balances, transfers);
    }

    @Test
    @DisplayName("a circular debt chain needs no payments at all")
    void cancelsCircularDebtsEntirely() {
        // A owes B, B owes C, C owes A, all for the same amount: everyone is already square.
        Map<Long, Long> balances = new LinkedHashMap<>();
        balances.put(1L, 0L);
        balances.put(2L, 0L);
        balances.put(3L, 0L);

        assertThat(DebtSimplifier.simplify(balances)).isEmpty();
    }

    @Test
    @DisplayName("never emits more than n-1 transfers")
    void respectsTheUpperBound() {
        Map<Long, Long> balances = new LinkedHashMap<>();
        balances.put(1L, 50_000L);
        balances.put(2L, 30_000L);
        balances.put(3L, -20_000L);
        balances.put(4L, -25_000L);
        balances.put(5L, -35_000L);

        List<DebtSimplifier.Transfer> transfers = DebtSimplifier.simplify(balances);

        assertThat(transfers.size()).isLessThanOrEqualTo(balances.size() - 1);
        assertSettles(balances, transfers);
    }

    @Test
    @DisplayName("randomised: any balanced group is fully settled by the plan")
    void settlesRandomisedGroups() {
        Random random = new Random(20260912L);

        for (int trial = 0; trial < 500; trial++) {
            int people = 2 + random.nextInt(12);
            Map<Long, Long> balances = new LinkedHashMap<>();
            long running = 0;
            for (long id = 1; id < people; id++) {
                long amount = random.nextInt(200_000) - 100_000;
                balances.put(id, amount);
                running += amount;
            }
            // The last participant absorbs the remainder so the group nets to zero, exactly as a
            // real ledger would.
            balances.put((long) people, -running);

            List<DebtSimplifier.Transfer> transfers = DebtSimplifier.simplify(balances);

            assertThat(transfers.size())
                    .as("trial %d must not exceed n-1 transfers", trial)
                    .isLessThanOrEqualTo(balances.size() - 1);
            assertSettles(balances, transfers);
        }
    }

    @Test
    @DisplayName("every transfer is a positive amount between two different people")
    void producesOnlySensibleTransfers() {
        Map<Long, Long> balances = new LinkedHashMap<>();
        balances.put(1L, 15_000L);
        balances.put(2L, -5_000L);
        balances.put(3L, -10_000L);

        for (DebtSimplifier.Transfer t : DebtSimplifier.simplify(balances)) {
            assertThat(t.amountPaise()).isPositive();
            assertThat(t.fromUserId()).isNotEqualTo(t.toUserId());
        }
    }

    @Test
    @DisplayName("members who are already square are left out of the plan")
    void ignoresZeroBalances() {
        Map<Long, Long> balances = new LinkedHashMap<>();
        balances.put(1L, 10_000L);
        balances.put(2L, -10_000L);
        balances.put(3L, 0L);

        List<DebtSimplifier.Transfer> transfers = DebtSimplifier.simplify(balances);

        assertThat(transfers).hasSize(1);
        assertThat(transfers.get(0).fromUserId()).isEqualTo(2L);
        assertThat(transfers.get(0).toUserId()).isEqualTo(1L);
        assertThat(transfers.get(0).amountPaise()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("refuses to plan payments from a corrupt ledger")
    void rejectsUnbalancedInput() {
        Map<Long, Long> broken = Map.of(1L, 10_000L, 2L, -9_999L);

        assertThatThrownBy(() -> DebtSimplifier.simplify(broken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sum to");
    }

    @Test
    void handlesEmptyAndNullInput() {
        assertThat(DebtSimplifier.simplify(Map.of())).isEmpty();
        assertThat(DebtSimplifier.simplify(null)).isEmpty();
    }

    /** Applies the plan to the starting balances and asserts everyone ends up at exactly zero. */
    private static void assertSettles(Map<Long, Long> balances, List<DebtSimplifier.Transfer> transfers) {
        Map<Long, Long> after = new HashMap<>(balances);
        for (DebtSimplifier.Transfer t : transfers) {
            after.merge(t.fromUserId(), t.amountPaise(), Long::sum);
            after.merge(t.toUserId(), -t.amountPaise(), Long::sum);
        }
        assertThat(after.values()).allSatisfy(v -> assertThat(v).isZero());
    }
}
