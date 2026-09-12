package com.settleup.group.dto;

import java.time.Instant;
import java.util.List;

public record GroupView(
        Long id,
        String name,
        String currency,
        long version,
        Instant createdAt,
        List<MemberView> members) {
}
