package com.settleup.settlement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Reduces a group's tangled web of IOUs to a small set of payments.
 *
 * <p><b>The problem.</b> After a trip, the raw expense history might say "Aditi owes Rohan 400,
 * Rohan owes Sana 400, Sana owes Aditi 250..." — a dozen edges that nobody wants to execute.
 * Only the <i>net</i> position of each person matters, so we discard the individual debts and
 * re-derive a minimal payment set from the net balances alone.
 *
 * <p><b>The algorithm.</b> Repeatedly match the largest creditor with the largest debtor and
 * transfer {@code min(|credit|, |debt|)}. Each transfer fully zeroes out at least one person, so
 * with {@code n} non-zero participants we emit at most {@code n - 1} transfers.
 *
 * <p><b>Honest caveat.</b> This is a greedy heuristic, not a proven optimum. Finding the true
 * minimum number of transfers is NP-hard (it generalises the subset-sum / partition problem: you
 * would have to find every subset of participants whose balances cancel to zero and settle each
 * independently). The greedy bound of {@code n - 1} is good enough in practice — real groups are
 * small, and n-1 is already a large reduction from the raw edge list — and it runs in
 * {@code O(n log n)} instead of exponential time.
 */
public final class DebtSimplifier {

    private DebtSimplifier() {
    }

    /**
     * A single payment instruction: {@code fromUserId} hands {@code amountPaise} to {@code toUserId}.
     */
    public record Transfer(Long fromUserId, Long toUserId, long amountPaise) {
    }

    private record Position(Long userId, long amountPaise) {
    }

    /**
     * @param netBalances net position per user in paise; positive means the group owes them money,
     *                    negative means they owe the group. Must sum to zero.
     * @return payment instructions, at most {@code netBalances.size() - 1} of them
     */
    public static List<Transfer> simplify(Map<Long, Long> netBalances) {
        if (netBalances == null || netBalances.isEmpty()) {
            return List.of();
        }

        long sum = netBalances.values().stream().mapToLong(Long::longValue).sum();
        if (sum != 0L) {
            // A non-zero sum means the ledger itself is corrupt; refusing to produce a payment plan
            // is far safer than telling people to transfer numbers derived from bad data.
            throw new IllegalStateException(
                    "cannot simplify debts: balances sum to " + sum + " paise instead of 0");
        }

        // Largest amount first; ties broken by user id purely so output is deterministic for tests.
        Comparator<Position> largestFirst = Comparator
                .comparingLong((Position p) -> Math.abs(p.amountPaise())).reversed()
                .thenComparing(Position::userId);

        PriorityQueue<Position> creditors = new PriorityQueue<>(largestFirst);
        PriorityQueue<Position> debtors = new PriorityQueue<>(largestFirst);

        netBalances.forEach((userId, balance) -> {
            if (balance > 0) {
                creditors.add(new Position(userId, balance));
            } else if (balance < 0) {
                debtors.add(new Position(userId, -balance));
            }
            // balance == 0 participants are already square and take no part in the plan
        });

        List<Transfer> transfers = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Position creditor = creditors.poll();
            Position debtor = debtors.poll();

            long amount = Math.min(creditor.amountPaise(), debtor.amountPaise());
            transfers.add(new Transfer(debtor.userId(), creditor.userId(), amount));

            long creditorLeft = creditor.amountPaise() - amount;
            long debtorLeft = debtor.amountPaise() - amount;

            // Exactly one of these is zero unless the two matched perfectly, which is why every
            // iteration removes at least one participant and the loop terminates.
            if (creditorLeft > 0) {
                creditors.add(new Position(creditor.userId(), creditorLeft));
            }
            if (debtorLeft > 0) {
                debtors.add(new Position(debtor.userId(), debtorLeft));
            }
        }

        return transfers;
    }
}
