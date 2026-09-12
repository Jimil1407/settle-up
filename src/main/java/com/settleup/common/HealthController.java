package com.settleup.common;

import com.settleup.group.GroupService;
import com.settleup.ledger.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final LedgerService ledgerService;
    private final GroupService groupService;

    /**
     * Liveness and readiness in one. Actually touches the database, because a process that is
     * running but cannot reach its database is not healthy in any useful sense.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(Map.of("status", "UP", "database", "UP"));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "DOWN", "database", "DOWN"));
        }
    }

    /**
     * Reports the group's ledger imbalance, which must always be zero.
     *
     * <p>This is the sum-to-zero invariant exposed as a live endpoint rather than only as a test
     * assertion: point a load generator at the group, poll this, and watch it stay at zero. If it
     * is ever non-zero, some balance write escaped the locking protocol and the group's money can
     * no longer be trusted.
     */
    @GetMapping("/groups/{groupId}/ledger-check")
    public Map<String, Object> ledgerCheck(@PathVariable Long groupId,
                                           @AuthenticationPrincipal Long userId) {
        groupService.requireMember(groupId, userId);
        long imbalance = ledgerService.imbalanceOf(groupId);
        return Map.of(
                "groupId", groupId,
                "imbalancePaise", imbalance,
                "balanced", imbalance == 0L);
    }
}
