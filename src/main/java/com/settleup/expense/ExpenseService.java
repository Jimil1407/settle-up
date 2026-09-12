package com.settleup.expense;

import com.settleup.common.Money;
import com.settleup.common.NotFoundException;
import com.settleup.common.ValidationException;
import com.settleup.expense.dto.CreateExpenseRequest;
import com.settleup.expense.dto.ExpenseSplitView;
import com.settleup.expense.dto.ExpenseView;
import com.settleup.group.ExpenseGroup;
import com.settleup.group.GroupLockService;
import com.settleup.group.GroupMemberRepository;
import com.settleup.group.GroupService;
import com.settleup.infra.idempotency.IdempotencyStore;
import com.settleup.ledger.LedgerService;
import com.settleup.ledger.LedgerSource;
import com.settleup.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseService {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository splitRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupLockService groupLockService;
    private final GroupService groupService;
    private final LedgerService ledgerService;
    private final IdempotencyStore idempotencyStore;
    private final NotificationService notificationService;

    /**
     * Records an expense and the ledger entries it implies.
     *
     * <p>Ordering inside the transaction is deliberate:
     * <ol>
     *   <li>take the group's write lock, which serialises this against every other balance write
     *       for the same group and bumps the version;</li>
     *   <li>validate membership and compute the split;</li>
     *   <li>persist the expense, its splits, and a balanced set of ledger entries.</li>
     * </ol>
     * Because all of that is one transaction, a group can never be observed with an expense but no
     * matching ledger entries, or with a version that disagrees with its balances.
     */
    @Transactional
    public ExpenseView create(Long groupId, Long actorUserId, CreateExpenseRequest request, String idempotencyKey) {
        groupService.requireMember(groupId, actorUserId);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = expenseRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return toView(existing.get());
            }
            if (!idempotencyStore.tryAcquire(idempotencyKey, IDEMPOTENCY_TTL)) {
                // An identical request is in flight right now. Reporting a conflict is safer than
                // waiting, because the caller can simply re-read the expense list.
                throw new ValidationException(
                        "a request with this Idempotency-Key is already being processed");
            }
        }

        try {
            return doCreate(groupId, actorUserId, request, idempotencyKey);
        } catch (RuntimeException ex) {
            // Free the key so a genuine retry after a transient failure is not blocked for 24h.
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                idempotencyStore.release(idempotencyKey);
            }
            throw ex;
        }
    }

    private ExpenseView doCreate(Long groupId, Long actorUserId, CreateExpenseRequest request, String idempotencyKey) {
        // Lock first: everything after this point is serialised per group.
        ExpenseGroup group = groupLockService.lockForBalanceWrite(groupId);

        // Re-check under the lock. Two duplicate requests for the same group are serialised by it,
        // so the second one lands here after the first has committed and returns that result
        // instead of inserting again. This is what actually resolves the common duplicate race;
        // the unique index behind it only has to catch a client reusing one key across different
        // groups, which is a genuine client bug and correctly surfaces as a 409.
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = expenseRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return toView(existing.get());
            }
        }

        long totalPaise = Money.rupeesToPaise(request.amount());
        if (totalPaise <= 0) {
            throw new ValidationException("amount must be greater than zero");
        }

        Set<Long> memberIds = new HashSet<>(memberRepository.findUserIdsByGroupId(groupId));
        if (!memberIds.contains(request.paidByUserId())) {
            throw new ValidationException("the payer is not a member of this group");
        }

        Map<Long, Long> shares = computeShares(request, totalPaise, memberIds);

        Expense expense = expenseRepository.save(Expense.builder()
                .groupId(groupId)
                .description(request.description().trim())
                .totalPaise(totalPaise)
                .paidByUserId(request.paidByUserId())
                .splitType(request.splitType())
                .createdBy(actorUserId)
                .idempotencyKey(blankToNull(idempotencyKey))
                .createdAt(Instant.now())
                .build());

        List<ExpenseSplit> splits = shares.entrySet().stream()
                .map(e -> ExpenseSplit.builder()
                        .expenseId(expense.getId())
                        .userId(e.getKey())
                        .sharePaise(e.getValue())
                        .build())
                .toList();
        splitRepository.saveAll(splits);

        ledgerService.append(groupId, ledgerDeltas(request.paidByUserId(), totalPaise, shares),
                LedgerSource.EXPENSE, expense.getId());

        // Invalidates the version-keyed balance cache and any settlement plan clients are holding.
        groupLockService.markBalanceChanged(groupId);

        notificationService.expenseAdded(groupId, expense.getId(), expense.getDescription(), totalPaise);
        log.debug("expense {} recorded in group {}", expense.getId(), groupId);

        return toView(expense, splits);
    }

    /**
     * Turns "who paid" and "who owes what" into a set of signed deltas that sums to zero.
     *
     * <p>The payer is credited the full total because they are out of pocket by that much, and each
     * participant is debited their share. If the payer is also a participant, the two entries for
     * them are merged rather than written twice.
     */
    static Map<Long, Long> ledgerDeltas(Long payerId, long totalPaise, Map<Long, Long> shares) {
        Map<Long, Long> deltas = new LinkedHashMap<>();
        deltas.merge(payerId, totalPaise, Long::sum);
        shares.forEach((userId, share) -> deltas.merge(userId, -share, Long::sum));
        return deltas;
    }

    private Map<Long, Long> computeShares(CreateExpenseRequest request, long totalPaise, Set<Long> memberIds) {
        Map<Long, Long> shares = switch (request.splitType()) {
            case EQUAL -> {
                List<Long> participants = (request.participantIds() == null || request.participantIds().isEmpty())
                        // Defaulting to "everyone" matches what people expect from a group expense.
                        ? new ArrayList<>(memberIds)
                        : request.participantIds();
                yield Money.splitEvenly(totalPaise, participants);
            }
            case WEIGHTED -> {
                if (request.weights() == null || request.weights().isEmpty()) {
                    throw new ValidationException("weights are required for a WEIGHTED split");
                }
                yield Money.splitByWeight(totalPaise, request.weights());
            }
            case EXACT -> {
                if (request.exactAmounts() == null || request.exactAmounts().isEmpty()) {
                    throw new ValidationException("exactAmounts are required for an EXACT split");
                }
                Map<Long, Long> asPaise = new LinkedHashMap<>();
                request.exactAmounts().forEach((userId, rupees) ->
                        asPaise.put(userId, Money.rupeesToPaise(rupees)));
                yield Money.validateExact(totalPaise, asPaise);
            }
        };

        if (shares.isEmpty()) {
            throw new ValidationException("an expense needs at least one participant");
        }
        for (Long userId : shares.keySet()) {
            if (!memberIds.contains(userId)) {
                throw new ValidationException("user " + userId + " is not a member of this group");
            }
        }
        return shares;
    }

    /**
     * Materialises one period of a recurring template, e.g. September's rent.
     *
     * <p>Safe to call repeatedly. The job that drives this is not guaranteed to run exactly once —
     * a restart, an overlapping schedule or a second instance can all trigger it again — so
     * "charge rent once a month" cannot rely on the scheduler behaving. Correctness comes from the
     * unique index on {@code (recurring_template_id, period_key)}: the pre-check below handles the
     * common case cheaply, and the constraint handles the race.
     *
     * <p>Templates split equally across the group's current members, so someone who joins midway
     * starts sharing rent from the next period onward.
     *
     * @return the new expense id, or empty if this period was already generated
     */
    @Transactional
    public Optional<Long> generateRecurring(Long groupId,
                                            Long templateId,
                                            String periodKey,
                                            String description,
                                            long totalPaise,
                                            Long paidByUserId,
                                            SplitType splitType) {
        if (expenseRepository.existsByRecurringTemplateIdAndPeriodKey(templateId, periodKey)) {
            return Optional.empty();
        }

        ExpenseGroup group = groupLockService.lockForBalanceWrite(groupId);

        List<Long> memberIds = memberRepository.findUserIdsByGroupId(groupId);
        if (memberIds.isEmpty()) {
            log.warn("skipping recurring template {}: group {} has no members", templateId, groupId);
            return Optional.empty();
        }
        if (!memberIds.contains(paidByUserId)) {
            log.warn("skipping recurring template {}: payer {} is no longer a member of group {}",
                    templateId, paidByUserId, groupId);
            return Optional.empty();
        }

        Map<Long, Long> shares = Money.splitEvenly(totalPaise, memberIds);

        Expense expense;
        try {
            expense = expenseRepository.saveAndFlush(Expense.builder()
                    .groupId(groupId)
                    .description(description)
                    .totalPaise(totalPaise)
                    .paidByUserId(paidByUserId)
                    .splitType(splitType)
                    .createdBy(paidByUserId)
                    .recurringTemplateId(templateId)
                    .periodKey(periodKey)
                    .createdAt(Instant.now())
                    .build());
        } catch (DataIntegrityViolationException ex) {
            // Another instance generated this period between our check and our insert.
            log.info("recurring template {} period {} was already generated concurrently",
                    templateId, periodKey);
            return Optional.empty();
        }

        splitRepository.saveAll(shares.entrySet().stream()
                .map(e -> ExpenseSplit.builder()
                        .expenseId(expense.getId())
                        .userId(e.getKey())
                        .sharePaise(e.getValue())
                        .build())
                .toList());

        ledgerService.append(groupId, ledgerDeltas(paidByUserId, totalPaise, shares),
                LedgerSource.EXPENSE, expense.getId());
        groupLockService.markBalanceChanged(groupId);

        notificationService.recurringExpenseGenerated(groupId, expense.getId(), description);
        return Optional.of(expense.getId());
    }

    @Transactional(readOnly = true)
    public List<ExpenseView> list(Long groupId, Long actorUserId) {
        groupService.requireMember(groupId, actorUserId);
        List<Expense> expenses = expenseRepository.findByGroupIdOrderByCreatedAtDescIdDesc(groupId);
        if (expenses.isEmpty()) {
            return List.of();
        }

        // One query for all splits instead of one per expense; the N+1 here is easy to miss and
        // shows up immediately on a group with a few hundred expenses.
        Map<Long, List<ExpenseSplit>> splitsByExpense =
                splitRepository.findByExpenseIdIn(expenses.stream().map(Expense::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(ExpenseSplit::getExpenseId));

        Set<Long> userIds = new HashSet<>();
        expenses.forEach(e -> userIds.add(e.getPaidByUserId()));
        splitsByExpense.values().forEach(list -> list.forEach(s -> userIds.add(s.getUserId())));
        Map<Long, String> names = groupService.displayNames(userIds);

        return expenses.stream()
                .map(e -> buildView(e, splitsByExpense.getOrDefault(e.getId(), List.of()), names))
                .toList();
    }

    @Transactional(readOnly = true)
    public ExpenseView get(Long groupId, Long expenseId, Long actorUserId) {
        groupService.requireMember(groupId, actorUserId);
        Expense expense = expenseRepository.findById(expenseId)
                .filter(e -> e.getGroupId().equals(groupId))
                .orElseThrow(() -> new NotFoundException("expense " + expenseId + " not found"));
        return toView(expense);
    }

    private ExpenseView toView(Expense expense) {
        return toView(expense, splitRepository.findByExpenseId(expense.getId()));
    }

    private ExpenseView toView(Expense expense, List<ExpenseSplit> splits) {
        Set<Long> userIds = new HashSet<>();
        userIds.add(expense.getPaidByUserId());
        splits.forEach(s -> userIds.add(s.getUserId()));
        return buildView(expense, splits, groupService.displayNames(userIds));
    }

    private ExpenseView buildView(Expense expense, List<ExpenseSplit> splits, Map<Long, String> names) {
        Map<Long, String> safeNames = names == null ? new HashMap<>() : names;
        List<ExpenseSplitView> splitViews = splits.stream()
                .map(s -> new ExpenseSplitView(
                        s.getUserId(),
                        safeNames.getOrDefault(s.getUserId(), "Unknown"),
                        s.getSharePaise(),
                        Money.paiseToRupees(s.getSharePaise())))
                .toList();

        return new ExpenseView(
                expense.getId(),
                expense.getGroupId(),
                expense.getDescription(),
                expense.getTotalPaise(),
                Money.paiseToRupees(expense.getTotalPaise()),
                Money.format(expense.getTotalPaise()),
                expense.getPaidByUserId(),
                safeNames.getOrDefault(expense.getPaidByUserId(), "Unknown"),
                expense.getSplitType(),
                expense.getRecurringTemplateId() != null,
                expense.getCreatedAt(),
                splitViews);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
