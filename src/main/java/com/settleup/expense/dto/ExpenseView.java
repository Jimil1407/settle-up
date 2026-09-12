package com.settleup.expense.dto;

import com.settleup.expense.SplitType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ExpenseView(
        Long id,
        Long groupId,
        String description,
        long totalPaise,
        BigDecimal totalRupees,
        String formattedTotal,
        Long paidByUserId,
        String paidByName,
        SplitType splitType,
        boolean recurring,
        Instant createdAt,
        List<ExpenseSplitView> splits) {
}
