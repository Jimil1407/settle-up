package com.settleup.support;

import com.settleup.expense.SplitType;
import com.settleup.expense.dto.CreateExpenseRequest;
import com.settleup.group.ExpenseGroup;
import com.settleup.group.ExpenseGroupRepository;
import com.settleup.group.GroupMember;
import com.settleup.group.GroupMemberRepository;
import com.settleup.user.User;
import com.settleup.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared fixture for the integration tests.
 *
 * <p>Every test builds its own users and its own group. The H2 database is shared across the suite
 * for speed, so isolating on data rather than on schema keeps the tests independent without paying
 * to rebuild the schema between classes.
 */
@SpringBootTest
public abstract class IntegrationTestBase {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected ExpenseGroupRepository groupRepository;
    @Autowired
    protected GroupMemberRepository memberRepository;

    protected User newUser(String name) {
        int n = SEQUENCE.incrementAndGet();
        return userRepository.save(User.builder()
                .email(name.toLowerCase() + "-" + n + "@example.com")
                .displayName(name)
                .passwordHash("$2a$10$notarealhashusedonlyintests000000000000000000000000")
                .createdAt(Instant.now())
                .build());
    }

    protected ExpenseGroup newGroup(String name, List<User> members) {
        ExpenseGroup group = groupRepository.save(ExpenseGroup.builder()
                .name(name)
                .currency("INR")
                .createdBy(members.get(0).getId())
                .createdAt(Instant.now())
                .build());
        for (User member : members) {
            memberRepository.save(GroupMember.builder()
                    .groupId(group.getId())
                    .userId(member.getId())
                    .joinedAt(Instant.now())
                    .build());
        }
        return group;
    }

    protected List<User> newUsers(String... names) {
        List<User> users = new ArrayList<>();
        for (String name : names) {
            users.add(newUser(name));
        }
        return users;
    }

    protected static CreateExpenseRequest equalExpense(String description, String rupees, Long paidBy) {
        return new CreateExpenseRequest(
                description, new BigDecimal(rupees), paidBy, SplitType.EQUAL, null, null, null);
    }

    protected static CreateExpenseRequest equalExpense(String description, String rupees, Long paidBy,
                                                       List<Long> participants) {
        return new CreateExpenseRequest(
                description, new BigDecimal(rupees), paidBy, SplitType.EQUAL, participants, null, null);
    }
}
