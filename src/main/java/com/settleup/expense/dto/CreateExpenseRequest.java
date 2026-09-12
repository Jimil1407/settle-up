package com.settleup.expense.dto;

import com.settleup.expense.SplitType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Amounts are accepted as decimal rupees (what the user typed) and converted to paise once, at the
 * edge. Past this boundary the system only ever deals in whole paise.
 */
public record CreateExpenseRequest(
        @NotBlank @Size(max = 200) String description,

        @NotNull
        @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
        @Digits(integer = 10, fraction = 2, message = "amount cannot have more than 2 decimal places")
        BigDecimal amount,

        @NotNull Long paidByUserId,

        @NotNull SplitType splitType,

        /** Participants for an EQUAL split. Ignored for other split types. */
        List<Long> participantIds,

        /** userId -> weight, for a WEIGHTED split. */
        Map<Long, Integer> weights,

        /** userId -> exact rupee amount, for an EXACT split. Must sum to {@code amount}. */
        Map<Long, BigDecimal> exactAmounts) {
}
