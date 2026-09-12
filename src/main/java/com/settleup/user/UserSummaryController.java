package com.settleup.user;

import com.settleup.common.Money;
import com.settleup.group.ExpenseGroup;
import com.settleup.group.ExpenseGroupRepository;
import com.settleup.group.GroupMemberRepository;
import com.settleup.ledger.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserSummaryController {

    private final GroupMemberRepository memberRepository;
    private final ExpenseGroupRepository groupRepository;
    private final LedgerService ledgerService;
    private final UserRepository userRepository;

    public record GroupBalanceSummary(Long groupId, String groupName, long balancePaise, String formatted) {
    }

    /**
     * The "so, where do I actually stand?" view across every group.
     *
     * <p>{@code netPaise} is the sum of the user's position in all groups. It is reported alongside
     * the separate owed/owing totals because they answer different questions: a net of zero can
     * still mean you owe one flatmate a lot and another owes you the same.
     */
    public record MeSummary(
            Long userId,
            String displayName,
            String email,
            long netPaise,
            BigDecimal netRupees,
            String formattedNet,
            long totalOwedToYouPaise,
            long totalYouOwePaise,
            List<GroupBalanceSummary> groups) {
    }

    @GetMapping("/summary")
    @Transactional(readOnly = true)
    public MeSummary summary(@AuthenticationPrincipal Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        List<Long> groupIds = memberRepository.findGroupIdsByUserId(userId);

        List<GroupBalanceSummary> summaries = new ArrayList<>();
        long net = 0;
        long owedToYou = 0;
        long youOwe = 0;

        for (ExpenseGroup group : groupRepository.findAllById(groupIds)) {
            long balance = ledgerService.balanceOf(group.getId(), userId);
            net += balance;
            if (balance > 0) {
                owedToYou += balance;
            } else {
                youOwe += -balance;
            }
            summaries.add(new GroupBalanceSummary(
                    group.getId(), group.getName(), balance, Money.format(balance)));
        }

        summaries.sort((a, b) -> Long.compare(Math.abs(b.balancePaise()), Math.abs(a.balancePaise())));

        return new MeSummary(
                userId, user.getDisplayName(), user.getEmail(),
                net, Money.paiseToRupees(net), Money.format(net),
                owedToYou, youOwe, summaries);
    }
}
