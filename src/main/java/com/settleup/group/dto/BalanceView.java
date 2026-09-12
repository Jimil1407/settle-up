package com.settleup.group.dto;

import java.math.BigDecimal;

/**
 * One member's net position. Positive means the group owes them, negative means they owe the group.
 * Both the raw paise and a display-friendly rupee value are returned so clients never have to do
 * money arithmetic themselves.
 */
public record BalanceView(
        Long userId,
        String displayName,
        long balancePaise,
        BigDecimal balanceRupees,
        String formatted) {
}
