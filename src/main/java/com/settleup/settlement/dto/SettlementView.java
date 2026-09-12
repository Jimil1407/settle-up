package com.settleup.settlement.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record SettlementView(
        Long id,
        Long groupId,
        Long fromUserId,
        String fromName,
        Long toUserId,
        String toName,
        long amountPaise,
        BigDecimal amountRupees,
        String formatted,
        Instant createdAt) {
}
