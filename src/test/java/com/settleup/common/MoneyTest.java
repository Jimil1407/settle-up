package com.settleup.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The money rules the whole ledger rests on. If a split does not sum exactly to its total, the
 * sum-to-zero invariant breaks and every balance in the group becomes untrustworthy.
 */
class MoneyTest {

    @Test
    @DisplayName("the classic case: 100 split three ways loses nothing")
    void splitsHundredThreeWaysWithoutLosingAPaisa() {
        Map<Long, Long> shares = Money.splitEvenly(10_000L, List.of(1L, 2L, 3L));

        // 33.34 + 33.33 + 33.33 = 100.00 exactly. Naive rounding would have given 33.33 each and
        // quietly lost a paisa.
        assertThat(shares.values()).containsExactly(3334L, 3333L, 3333L);
        assertThat(sum(shares)).isEqualTo(10_000L);
    }

    @ParameterizedTest(name = "{0} paise across {1} people sums back exactly")
    @CsvSource({
            "10000, 3", "10000, 7", "1, 2", "1, 7", "99, 100",
            "100000, 6", "333, 3", "0, 4", "7, 3", "123456789, 13"
    })
    void everySplitSumsBackToTheTotal(long totalPaise, int people) {
        List<Long> participants = participants(people);

        Map<Long, Long> shares = Money.splitEvenly(totalPaise, participants);

        assertThat(shares).hasSize(people);
        assertThat(sum(shares)).isEqualTo(totalPaise);
    }

    @Test
    @DisplayName("shares never differ by more than one paisa")
    void sharesAreAsEvenAsPossible() {
        Map<Long, Long> shares = Money.splitEvenly(10_000L, participants(7));

        long min = shares.values().stream().mapToLong(Long::longValue).min().orElseThrow();
        long max = shares.values().stream().mapToLong(Long::longValue).max().orElseThrow();
        assertThat(max - min).isLessThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("negative totals (refunds) also sum back exactly")
    void handlesNegativeTotals() {
        Map<Long, Long> shares = Money.splitEvenly(-10_000L, List.of(1L, 2L, 3L));

        assertThat(sum(shares)).isEqualTo(-10_000L);
        assertThat(shares.values()).allSatisfy(v -> assertThat(v).isNegative());
    }

    @Test
    void splitIsDeterministicRegardlessOfInputOrder() {
        Map<Long, Long> a = Money.splitEvenly(10_000L, List.of(3L, 1L, 2L));
        Map<Long, Long> b = Money.splitEvenly(10_000L, List.of(1L, 2L, 3L));

        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("weighted split: a couple counts as two units")
    void splitsByWeight() {
        // 3000 paise, one person (1 unit) and a couple (2 units) => 1000 / 2000
        Map<Long, Long> shares = Money.splitByWeight(3_000L, Map.of(1L, 1, 2L, 2));

        assertThat(shares.get(1L)).isEqualTo(1_000L);
        assertThat(shares.get(2L)).isEqualTo(2_000L);
        assertThat(sum(shares)).isEqualTo(3_000L);
    }

    @Test
    @DisplayName("weighted split distributes indivisible remainders without losing them")
    void weightedSplitHandlesRemainders() {
        Map<Long, Long> shares = Money.splitByWeight(10_000L, Map.of(1L, 1, 2L, 1, 3L, 1));

        assertThat(sum(shares)).isEqualTo(10_000L);
    }

    @Test
    void weightedSplitRejectsNonPositiveWeights() {
        assertThatThrownBy(() -> Money.splitByWeight(1_000L, Map.of(1L, 0)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("must be positive");
    }

    @Test
    @DisplayName("exact split is rejected when the parts do not add up")
    void exactSplitMustSumToTotal() {
        assertThatThrownBy(() -> Money.validateExact(10_000L, Map.of(1L, 3_000L, 2L, 3_000L)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("add up to");

        assertThat(Money.validateExact(10_000L, Map.of(1L, 4_000L, 2L, 6_000L)))
                .containsEntry(1L, 4_000L)
                .containsEntry(2L, 6_000L);
    }

    @Test
    void convertsRupeesToPaiseWithoutFloatingPointDrift() {
        assertThat(Money.rupeesToPaise(new BigDecimal("0.10"))).isEqualTo(10L);
        assertThat(Money.rupeesToPaise(new BigDecimal("0.20"))).isEqualTo(20L);
        assertThat(Money.rupeesToPaise(new BigDecimal("1234.56"))).isEqualTo(123_456L);

        // The reason paise exist at all: this identity does not hold for doubles.
        assertThat(Money.rupeesToPaise(new BigDecimal("0.10")) + Money.rupeesToPaise(new BigDecimal("0.20")))
                .isEqualTo(Money.rupeesToPaise(new BigDecimal("0.30")));
    }

    @Test
    void formatsAmountsForDisplay() {
        assertThat(Money.format(123_456L)).isEqualTo("\u20b91234.56");
        assertThat(Money.format(-5_000L)).isEqualTo("-\u20b950.00");
        assertThat(Money.format(0L)).isEqualTo("\u20b90.00");
    }

    @Test
    void rejectsEmptyParticipantList() {
        assertThatThrownBy(() -> Money.splitEvenly(1_000L, List.of()))
                .isInstanceOf(ValidationException.class);
    }

    private static List<Long> participants(int n) {
        return java.util.stream.LongStream.rangeClosed(1, n).boxed().toList();
    }

    private static long sum(Map<Long, Long> shares) {
        return shares.values().stream().mapToLong(Long::longValue).sum();
    }
}
