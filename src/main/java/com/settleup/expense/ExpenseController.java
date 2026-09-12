package com.settleup.expense;

import com.settleup.expense.dto.CreateExpenseRequest;
import com.settleup.expense.dto.ExpenseView;
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
@RequestMapping("/api/groups/{groupId}/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    /**
     * @param idempotencyKey optional; supplying it makes the call safe to retry after a timeout or
     *                       a dropped connection, which on a phone is the normal case rather than
     *                       the exceptional one
     */
    @PostMapping
    public ResponseEntity<ExpenseView> create(@PathVariable Long groupId,
                                              @AuthenticationPrincipal Long userId,
                                              @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                              @Valid @RequestBody CreateExpenseRequest request) {
        ExpenseView view = expenseService.create(groupId, userId, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @GetMapping
    public List<ExpenseView> list(@PathVariable Long groupId, @AuthenticationPrincipal Long userId) {
        return expenseService.list(groupId, userId);
    }

    @GetMapping("/{expenseId}")
    public ExpenseView get(@PathVariable Long groupId,
                           @PathVariable Long expenseId,
                           @AuthenticationPrincipal Long userId) {
        return expenseService.get(groupId, expenseId, userId);
    }
}
