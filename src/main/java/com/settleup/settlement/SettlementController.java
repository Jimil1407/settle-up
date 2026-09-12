package com.settleup.settlement;

import com.settleup.settlement.dto.RecordSettlementRequest;
import com.settleup.settlement.dto.SettlementPlanResponse;
import com.settleup.settlement.dto.SettlementView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/groups/{groupId}")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    /** The minimal set of payments that squares the group, plus the version it was derived from. */
    @GetMapping("/settlement-plan")
    public SettlementPlanResponse plan(@PathVariable Long groupId, @AuthenticationPrincipal Long userId) {
        return settlementService.plan(groupId, userId);
    }

    @PostMapping("/settlements")
    public ResponseEntity<SettlementView> record(@PathVariable Long groupId,
                                                 @AuthenticationPrincipal Long userId,
                                                 @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                 @Valid @RequestBody RecordSettlementRequest request) {
        SettlementView view = settlementService.record(groupId, userId, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @GetMapping("/settlements")
    public List<SettlementView> list(@PathVariable Long groupId, @AuthenticationPrincipal Long userId) {
        return settlementService.list(groupId, userId);
    }
}
