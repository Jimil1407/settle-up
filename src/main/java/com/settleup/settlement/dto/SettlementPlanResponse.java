package com.settleup.settlement.dto;

import java.util.List;

/**
 * A minimal set of payments that squares the whole group.
 *
 * <p>{@code version} is the group version these transfers were derived from. Clients must echo it
 * back when recording a payment so the server can reject a plan that has since gone stale.
 */
public record SettlementPlanResponse(
        Long groupId,
        long version,
        int rawDebtCount,
        int simplifiedTransferCount,
        List<TransferView> transfers) {
}
