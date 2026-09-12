package com.settleup.settlement.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RecordSettlementRequest(
        @NotNull Long fromUserId,
        @NotNull Long toUserId,

        @NotNull
        @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
        @Digits(integer = 10, fraction = 2, message = "amount cannot have more than 2 decimal places")
        BigDecimal amount,

        /**
         * The group version the client's settlement plan was computed from. Optional: omit it to
         * record an ad-hoc payment that was not derived from a plan.
         */
        Long expectedVersion) {
}
