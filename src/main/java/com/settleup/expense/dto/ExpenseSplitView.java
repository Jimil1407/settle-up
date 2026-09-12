package com.settleup.expense.dto;

import java.math.BigDecimal;

public record ExpenseSplitView(
        Long userId,
        String displayName,
        long sharePaise,
        BigDecimal shareRupees) {
}
