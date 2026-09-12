package com.settleup.settlement.dto;

import java.math.BigDecimal;

public record TransferView(
        Long fromUserId,
        String fromName,
        Long toUserId,
        String toName,
        long amountPaise,
        BigDecimal amountRupees,
        String formatted) {
}
