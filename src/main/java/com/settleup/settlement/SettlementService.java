package com.settleup.settlement;

import com.settleup.common.Money;
import com.settleup.common.StalePlanException;
import com.settleup.common.ValidationException;
import com.settleup.group.ExpenseGroup;
import com.settleup.group.GroupLockService;
import com.settleup.group.GroupMemberRepository;
import com.settleup.group.GroupService;
import com.settleup.infra.cache.BalanceCache;
import com.settleup.infra.idempotency.IdempotencyStore;
import com.settleup.ledger.LedgerService;
import com.settleup.ledger.LedgerSource;
import com.settleup.notification.NotificationService;
import com.settleup.settlement.dto.RecordSettlementRequest;
import com.settleup.settlement.dto.SettlementPlanResponse;
import com.settleup.settlement.dto.SettlementView;
import com.settleup.settlement.dto.TransferView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementService {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final SettlementRepository settlementRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupLockService groupLockService;
    private final GroupService groupService;
    private final LedgerService ledgerService;
    private final BalanceCache balanceCache;
    private final IdempotencyStore idempotencyStore;
    private final NotificationService notificationService;

    /**
     * Computes the minimal set of payments that would square the group.
     *
     * <p>Read-only and safe to call as often as the UI likes. The returned {@code version} is what
     * makes acting on the plan safe later.
     */
    @Transactional(readOnly = true)
    public SettlementPlanResponse plan(Long groupId, Long actorUserId) {
        groupService.requireMember(groupId, actorUserId);
        ExpenseGroup group = groupService.loadGroup(groupId);

        Map<Long, Long> balances = balanceCache.get(groupId, group.getVersion())
                .orElseGet(() -> {
                    Map<Long, Long> computed = ledgerService.netBalancesForMembers(
                            groupId, memberRepository.findUserIdsByGroupId(groupId));
                    balanceCache.put(groupId, group.getVersion(), computed);
                    return computed;
                });

        List<DebtSimplifier.Transfer> transfers = DebtSimplifier.simplify(balances);

        Set<Long> userIds = new HashSet<>();
        transfers.forEach(t -> {
            userIds.add(t.fromUserId());
            userIds.add(t.toUserId());
        });
        Map<Long, String> names = groupService.displayNames(userIds);

        List<TransferView> views = transfers.stream()
                .map(t -> new TransferView(
                        t.fromUserId(), names.getOrDefault(t.fromUserId(), "Unknown"),
                        t.toUserId(), names.getOrDefault(t.toUserId(), "Unknown"),
                        t.amountPaise(),
                        Money.paiseToRupees(t.amountPaise()),
                        Money.format(t.amountPaise())))
                .toList();

        // How many people are non-zero, i.e. how many payments a naive "everyone settles with
        // everyone they owe" approach would involve, for contrast in the UI.
        int nonZero = (int) balances.values().stream().filter(v -> v != 0L).count();

        return new SettlementPlanResponse(groupId, group.getVersion(), nonZero, views.size(), views);
    }

    /**
     * Records that one member actually paid another back.
     *
     * <p>Two independent protections apply:
     * <ul>
     *   <li><b>Stale plan detection.</b> If the caller supplies the version their plan came from
     *       and the group has moved on, we reject with {@code 409} instead of applying a payment
     *       derived from balances that no longer exist.</li>
     *   <li><b>Idempotency.</b> A repeated {@code Idempotency-Key} returns the original settlement
     *       rather than paying twice.</li>
     * </ul>
     */
    @Transactional
    public SettlementView record(Long groupId, Long actorUserId, RecordSettlementRequest request, String idempotencyKey) {
        groupService.requireMember(groupId, actorUserId);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = settlementRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return toView(existing.get());
            }
            if (!idempotencyStore.tryAcquire(idempotencyKey, IDEMPOTENCY_TTL)) {
                throw new ValidationException(
                        "a request with this Idempotency-Key is already being processed");
            }
        }

        try {
            return doRecord(groupId, request, idempotencyKey);
        } catch (RuntimeException ex) {
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                idempotencyStore.release(idempotencyKey);
            }
            throw ex;
        }
    }

    private SettlementView doRecord(Long groupId, RecordSettlementRequest request, String idempotencyKey) {
        // Lock before reading the version, so the value we compare against cannot change underneath
        // us between the check and the write.
        ExpenseGroup group = groupLockService.lockForBalanceWrite(groupId);

        // Re-check under the lock: a duplicate request for this group is serialised behind us and
        // finds the already-committed settlement rather than paying a second time.
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = settlementRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return toView(existing.get());
            }
        }

        if (request.expectedVersion() != null && request.expectedVersion() != group.getVersion()) {
            throw new StalePlanException(request.expectedVersion(), group.getVersion());
        }

        long amountPaise = Money.rupeesToPaise(request.amount());
        if (amountPaise <= 0) {
            throw new ValidationException("settlement amount must be greater than zero");
        }
        if (request.fromUserId().equals(request.toUserId())) {
            throw new ValidationException("you cannot settle up with yourself");
        }

        Set<Long> memberIds = new HashSet<>(memberRepository.findUserIdsByGroupId(groupId));
        if (!memberIds.contains(request.fromUserId()) || !memberIds.contains(request.toUserId())) {
            throw new ValidationException("both people must be members of this group");
        }

        Settlement settlement = settlementRepository.save(Settlement.builder()
                .groupId(groupId)
                .fromUserId(request.fromUserId())
                .toUserId(request.toUserId())
                .amountPaise(amountPaise)
                .idempotencyKey((idempotencyKey == null || idempotencyKey.isBlank()) ? null : idempotencyKey)
                .createdAt(Instant.now())
                .build());

        // Paying down a debt moves the payer's net position up and the recipient's down; the two
        // deltas cancel, so the group still nets to zero.
        Map<Long, Long> deltas = new LinkedHashMap<>();
        deltas.put(request.fromUserId(), amountPaise);
        deltas.put(request.toUserId(), -amountPaise);
        ledgerService.append(groupId, deltas, LedgerSource.SETTLEMENT, settlement.getId());

        groupLockService.markBalanceChanged(groupId);

        notificationService.settlementRecorded(
                groupId, request.fromUserId(), request.toUserId(), amountPaise);

        return toView(settlement);
    }

    @Transactional(readOnly = true)
    public List<SettlementView> list(Long groupId, Long actorUserId) {
        groupService.requireMember(groupId, actorUserId);
        List<Settlement> settlements = settlementRepository.findByGroupIdOrderByCreatedAtDescIdDesc(groupId);
        if (settlements.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = new HashSet<>();
        settlements.forEach(s -> {
            userIds.add(s.getFromUserId());
            userIds.add(s.getToUserId());
        });
        Map<Long, String> names = groupService.displayNames(userIds);
        return settlements.stream().map(s -> toView(s, names)).toList();
    }

    private SettlementView toView(Settlement settlement) {
        return toView(settlement, groupService.displayNames(
                Set.of(settlement.getFromUserId(), settlement.getToUserId())));
    }

    private SettlementView toView(Settlement s, Map<Long, String> names) {
        return new SettlementView(
                s.getId(),
                s.getGroupId(),
                s.getFromUserId(), names.getOrDefault(s.getFromUserId(), "Unknown"),
                s.getToUserId(), names.getOrDefault(s.getToUserId(), "Unknown"),
                s.getAmountPaise(),
                Money.paiseToRupees(s.getAmountPaise()),
                Money.format(s.getAmountPaise()),
                s.getCreatedAt());
    }
}
