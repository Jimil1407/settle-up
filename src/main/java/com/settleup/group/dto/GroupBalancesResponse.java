package com.settleup.group.dto;

import java.util.List;

public record GroupBalancesResponse(
        Long groupId,
        long version,
        List<BalanceView> balances,
        boolean settled) {
}
