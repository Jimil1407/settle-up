package com.settleup.recurring;

import com.settleup.common.Money;
import com.settleup.common.NotFoundException;
import com.settleup.common.ValidationException;
import com.settleup.expense.SplitType;
import com.settleup.group.GroupMemberRepository;
import com.settleup.group.GroupService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/groups/{groupId}/recurring")
@RequiredArgsConstructor
public class RecurringExpenseController {

    private final RecurringExpenseTemplateRepository templateRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupService groupService;

    public record CreateTemplateRequest(
            @NotBlank @Size(max = 200) String description,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotNull Long paidByUserId,
            @Min(1) @Max(31) int dayOfMonth) {
    }

    public record TemplateView(
            Long id, String description, BigDecimal amount, Long paidByUserId,
            int dayOfMonth, boolean active) {
    }

    @PostMapping
    @Transactional
    public ResponseEntity<TemplateView> create(@PathVariable Long groupId,
                                               @AuthenticationPrincipal Long userId,
                                               @Valid @RequestBody CreateTemplateRequest request) {
        groupService.requireMember(groupId, userId);
        if (!memberRepository.existsByGroupIdAndUserId(groupId, request.paidByUserId())) {
            throw new ValidationException("the payer is not a member of this group");
        }
        RecurringExpenseTemplate saved = templateRepository.save(RecurringExpenseTemplate.builder()
                .groupId(groupId)
                .description(request.description().trim())
                .totalPaise(Money.rupeesToPaise(request.amount()))
                .paidByUserId(request.paidByUserId())
                // Templates always split equally; a weighted standing charge would need the weights
                // to be versioned alongside group membership, which is not worth the complexity here.
                .splitType(SplitType.EQUAL)
                .dayOfMonth(request.dayOfMonth())
                .active(true)
                .createdAt(Instant.now())
                .build());
        return ResponseEntity.status(HttpStatus.CREATED).body(toView(saved));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<TemplateView> list(@PathVariable Long groupId, @AuthenticationPrincipal Long userId) {
        groupService.requireMember(groupId, userId);
        return templateRepository.findByGroupId(groupId).stream().map(RecurringExpenseController::toView).toList();
    }

    @DeleteMapping("/{templateId}")
    @Transactional
    public ResponseEntity<Void> deactivate(@PathVariable Long groupId,
                                           @PathVariable Long templateId,
                                           @AuthenticationPrincipal Long userId) {
        groupService.requireMember(groupId, userId);
        RecurringExpenseTemplate template = templateRepository.findById(templateId)
                .filter(t -> t.getGroupId().equals(groupId))
                .orElseThrow(() -> new NotFoundException("template " + templateId + " not found"));
        // Deactivated rather than deleted, so already-generated expenses keep a valid reference.
        template.setActive(false);
        templateRepository.save(template);
        return ResponseEntity.noContent().build();
    }

    private static TemplateView toView(RecurringExpenseTemplate t) {
        return new TemplateView(t.getId(), t.getDescription(), Money.paiseToRupees(t.getTotalPaise()),
                t.getPaidByUserId(), t.getDayOfMonth(), t.isActive());
    }
}
