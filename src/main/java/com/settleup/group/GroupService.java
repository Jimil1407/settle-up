package com.settleup.group;

import com.settleup.common.ForbiddenException;
import com.settleup.common.Money;
import com.settleup.common.NotFoundException;
import com.settleup.common.RateLimitExceededException;
import com.settleup.common.ValidationException;
import com.settleup.group.dto.AddMemberRequest;
import com.settleup.group.dto.BalanceView;
import com.settleup.group.dto.CreateGroupRequest;
import com.settleup.group.dto.GroupBalancesResponse;
import com.settleup.group.dto.GroupView;
import com.settleup.group.dto.MemberView;
import com.settleup.infra.cache.BalanceCache;
import com.settleup.infra.ratelimit.RateLimiter;
import com.settleup.ledger.LedgerService;
import com.settleup.user.User;
import com.settleup.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupService {

    private static final int INVITES_PER_HOUR = 20;

    private final ExpenseGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final LedgerService ledgerService;
    private final BalanceCache balanceCache;
    private final RateLimiter rateLimiter;

    @Transactional
    public GroupView create(Long actorUserId, CreateGroupRequest request) {
        Instant now = Instant.now();
        ExpenseGroup group = groupRepository.save(ExpenseGroup.builder()
                .name(request.name().trim())
                .currency(request.currencyOrDefault())
                .createdBy(actorUserId)
                .createdAt(now)
                .build());
        memberRepository.save(GroupMember.builder()
                .groupId(group.getId())
                .userId(actorUserId)
                .joinedAt(now)
                .build());
        return toView(group);
    }

    @Transactional
    public GroupView addMember(Long groupId, Long actorUserId, AddMemberRequest request) {
        requireMember(groupId, actorUserId);

        if (!rateLimiter.tryConsume("invite:" + actorUserId, INVITES_PER_HOUR, Duration.ofHours(1))) {
            throw new RateLimitExceededException(
                    "too many invites sent; try again in a little while");
        }

        User invitee = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new NotFoundException("no user registered with that email"));

        if (memberRepository.existsByGroupIdAndUserId(groupId, invitee.getId())) {
            throw new ValidationException("that person is already in this group");
        }
        memberRepository.save(GroupMember.builder()
                .groupId(groupId)
                .userId(invitee.getId())
                .joinedAt(Instant.now())
                .build());

        return toView(loadGroup(groupId));
    }

    @Transactional(readOnly = true)
    public List<GroupView> listForUser(Long userId) {
        List<Long> groupIds = memberRepository.findGroupIdsByUserId(userId);
        if (groupIds.isEmpty()) {
            return List.of();
        }
        return groupRepository.findAllById(groupIds).stream()
                .sorted(Comparator.comparing(ExpenseGroup::getId).reversed())
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupView get(Long groupId, Long actorUserId) {
        requireMember(groupId, actorUserId);
        return toView(loadGroup(groupId));
    }

    /**
     * Returns every member's net balance.
     *
     * <p>Read path is cache-aside keyed by the group's current version, so a cache hit is only
     * possible when nothing has changed since it was written.
     */
    @Transactional(readOnly = true)
    public GroupBalancesResponse balances(Long groupId, Long actorUserId) {
        requireMember(groupId, actorUserId);
        ExpenseGroup group = loadGroup(groupId);

        Map<Long, Long> balances = balanceCache.get(groupId, group.getVersion())
                .orElseGet(() -> {
                    Map<Long, Long> computed = ledgerService.netBalancesForMembers(
                            groupId, memberRepository.findUserIdsByGroupId(groupId));
                    balanceCache.put(groupId, group.getVersion(), computed);
                    return computed;
                });

        Map<Long, String> names = displayNames(balances.keySet());
        List<BalanceView> views = balances.entrySet().stream()
                .map(e -> new BalanceView(
                        e.getKey(),
                        names.getOrDefault(e.getKey(), "Unknown"),
                        e.getValue(),
                        Money.paiseToRupees(e.getValue()),
                        Money.format(e.getValue())))
                .sorted(Comparator.comparing(BalanceView::displayName))
                .toList();

        boolean settled = balances.values().stream().allMatch(v -> v == 0L);
        return new GroupBalancesResponse(groupId, group.getVersion(), views, settled);
    }

    @Transactional(readOnly = true)
    public ExpenseGroup loadGroup(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("group " + groupId + " not found"));
    }

    /**
     * Authorisation check for every group-scoped operation. Deliberately throws {@code Forbidden}
     * rather than leaking whether the group exists at all.
     */
    @Transactional(readOnly = true)
    public void requireMember(Long groupId, Long userId) {
        if (!memberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new ForbiddenException("you are not a member of this group");
        }
    }

    @Transactional(readOnly = true)
    public Map<Long, String> displayNames(Iterable<Long> userIds) {
        List<Long> ids = new ArrayList<>();
        userIds.forEach(ids::add);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));
    }

    private GroupView toView(ExpenseGroup group) {
        List<GroupMember> members = memberRepository.findByGroupId(group.getId());
        Map<Long, User> users = userRepository.findByIdIn(
                        members.stream().map(GroupMember::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<MemberView> memberViews = members.stream()
                .map(m -> users.get(m.getUserId()))
                .filter(java.util.Objects::nonNull)
                .map(u -> new MemberView(u.getId(), u.getDisplayName(), u.getEmail()))
                .sorted(Comparator.comparing(MemberView::displayName))
                .toList();

        return new GroupView(
                group.getId(),
                group.getName(),
                group.getCurrency(),
                group.getVersion(),
                group.getCreatedAt(),
                memberViews);
    }
}
