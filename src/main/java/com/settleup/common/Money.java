package com.settleup.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All money in this system is stored and computed as a {@code long} count of paise
 * (the minor currency unit). Doubles are never used: 0.1 + 0.2 != 0.3 in binary
 * floating point, and a cent of drift in a shared ledger is a real bug.
 */
public final class Money {

    public static final long PAISE_PER_RUPEE = 100L;

    private Money() {
    }

    public static long rupeesToPaise(BigDecimal rupees) {
        if (rupees == null) {
            throw new IllegalArgumentException("amount is required");
        }
        return rupees.multiply(BigDecimal.valueOf(PAISE_PER_RUPEE))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    public static BigDecimal paiseToRupees(long paise) {
        return BigDecimal.valueOf(paise).divide(BigDecimal.valueOf(PAISE_PER_RUPEE), 2, RoundingMode.UNNECESSARY);
    }

    public static String format(long paise) {
        return (paise < 0 ? "-" : "") + "\u20b9" + paiseToRupees(Math.abs(paise)).toPlainString();
    }

    /**
     * Splits {@code totalPaise} across {@code participantIds} as evenly as possible using the
     * largest-remainder method: every participant gets {@code total / n}, and the first
     * {@code total % n} participants (ordered deterministically by id) absorb one extra paisa each.
     *
     * <p>This guarantees the shares sum back to exactly {@code totalPaise}. Naive rounding does not:
     * splitting 100.00 three ways as 33.33 each loses a paisa, and over many expenses a group's
     * balances would stop summing to zero.
     *
     * @return share per participant, in the iteration order of the sorted participant ids
     */
    public static Map<Long, Long> splitEvenly(long totalPaise, List<Long> participantIds) {
        requireParticipants(participantIds);
        List<Long> ordered = new ArrayList<>(participantIds);
        ordered.sort(Comparator.naturalOrder());

        int n = ordered.size();
        long remainder = totalPaise % n;

        // Math.floorMod keeps the extra-paisa distribution correct for negative totals (refunds).
        long extraCount = Math.floorMod(remainder, (long) n);
        long adjustedBase = (totalPaise - extraCount) / n;

        Map<Long, Long> shares = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            long share = adjustedBase + (i < extraCount ? 1 : 0);
            shares.put(ordered.get(i), share);
        }
        assertSumsTo(shares, totalPaise);
        return shares;
    }

    /**
     * Splits {@code totalPaise} in proportion to integer weights (shares/units), again using the
     * largest-remainder method so the parts sum exactly to the total.
     */
    public static Map<Long, Long> splitByWeight(long totalPaise, Map<Long, Integer> weights) {
        if (weights == null || weights.isEmpty()) {
            throw new ValidationException("at least one participant is required");
        }
        long totalWeight = 0;
        for (Map.Entry<Long, Integer> e : weights.entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) {
                throw new ValidationException("weight for user " + e.getKey() + " must be positive");
            }
            totalWeight += e.getValue();
        }

        List<Long> ordered = new ArrayList<>(weights.keySet());
        ordered.sort(Comparator.naturalOrder());

        Map<Long, Long> shares = new LinkedHashMap<>();
        List<long[]> remainders = new ArrayList<>();
        long distributed = 0;

        for (Long userId : ordered) {
            long weight = weights.get(userId);
            long numerator = totalPaise * weight;
            long share = Math.floorDiv(numerator, totalWeight);
            long rem = Math.floorMod(numerator, totalWeight);
            shares.put(userId, share);
            distributed += share;
            remainders.add(new long[]{userId, rem});
        }

        long leftover = totalPaise - distributed;
        // Hand the leftover paise to the largest fractional remainders first; ties break on lowest id
        // so the result is deterministic and reproducible in tests.
        remainders.sort((a, b) -> {
            int cmp = Long.compare(b[1], a[1]);
            return cmp != 0 ? cmp : Long.compare(a[0], b[0]);
        });
        for (int i = 0; i < leftover; i++) {
            long userId = remainders.get(i % remainders.size())[0];
            shares.put(userId, shares.get(userId) + 1);
        }

        assertSumsTo(shares, totalPaise);
        return shares;
    }

    /**
     * Validates caller-supplied exact shares. The whole point of the "exact" split type is that the
     * user controls every number, so the only rule we enforce is that they add up.
     */
    public static Map<Long, Long> validateExact(long totalPaise, Map<Long, Long> shares) {
        if (shares == null || shares.isEmpty()) {
            throw new ValidationException("at least one participant is required");
        }
        long sum = 0;
        for (Map.Entry<Long, Long> e : shares.entrySet()) {
            if (e.getValue() == null) {
                throw new ValidationException("share for user " + e.getKey() + " is required");
            }
            sum += e.getValue();
        }
        if (sum != totalPaise) {
            throw new ValidationException(String.format(
                    "exact shares add up to %s but the expense total is %s",
                    format(sum), format(totalPaise)));
        }
        return new LinkedHashMap<>(shares);
    }

    private static void requireParticipants(List<Long> participantIds) {
        if (participantIds == null || participantIds.isEmpty()) {
            throw new ValidationException("at least one participant is required");
        }
    }

    private static void assertSumsTo(Map<Long, Long> shares, long expected) {
        long sum = shares.values().stream().mapToLong(Long::longValue).sum();
        if (sum != expected) {
            // Unreachable if the algorithms above are correct; kept as a loud invariant guard.
            throw new IllegalStateException("split produced " + sum + " paise but expected " + expected);
        }
    }
}
