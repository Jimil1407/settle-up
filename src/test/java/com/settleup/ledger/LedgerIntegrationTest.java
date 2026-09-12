package com.settleup.ledger;

import com.settleup.expense.ExpenseService;
import com.settleup.expense.SplitType;
import com.settleup.expense.dto.CreateExpenseRequest;
import com.settleup.expense.dto.ExpenseView;
import com.settleup.group.ExpenseGroup;
import com.settleup.group.GroupService;
import com.settleup.settlement.SettlementService;
import com.settleup.settlement.dto.RecordSettlementRequest;
import com.settleup.support.IntegrationTestBase;
import com.settleup.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the core accounting: an expense produces a balanced set of ledger entries, and the
 * balances derived from them are the ones a human would expect.
 */
class LedgerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ExpenseService expenseService;
    @Autowired
    private SettlementService settlementService;
    @Autowired
    private LedgerService ledgerService;
    @Autowired
    private GroupService groupService;

    @Test
    @DisplayName("one person pays for dinner, everyone owes their share")
    void recordsASimpleSharedExpense() {
        List<User> users = newUsers("Aditi", "Rohan", "Sana");
        ExpenseGroup group = newGroup("Flat 402", users);
        Long aditi = users.get(0).getId();

        expenseService.create(group.getId(), aditi, equalExpense("Dinner", "900.00", aditi), null);

        Map<Long, Long> balances = ledgerService.netBalances(group.getId());

        // Aditi paid 900 and owes 300 of it, so she is up 600; the other two are down 300 each.
        assertThat(balances.get(aditi)).isEqualTo(60_000L);
        assertThat(balances.get(users.get(1).getId())).isEqualTo(-30_000L);
        assertThat(balances.get(users.get(2).getId())).isEqualTo(-30_000L);
        assertThat(ledgerService.imbalanceOf(group.getId())).isZero();
    }

    @Test
    @DisplayName("the awkward amount: 100 split three ways still nets to zero")
    void handlesIndivisibleAmounts() {
        List<User> users = newUsers("Aditi", "Rohan", "Sana");
        ExpenseGroup group = newGroup("Chai fund", users);
        Long payer = users.get(0).getId();

        expenseService.create(group.getId(), payer, equalExpense("Chai", "100.00", payer), null);

        Map<Long, Long> balances = ledgerService.netBalances(group.getId());

        // 10000 paise over 3 people is 3333.33 each. The largest-remainder rule gives the extra
        // paisa to the lowest id, which here is the payer: shares are 3334 / 3333 / 3333.
        // The payer fronted 10000 and owes 3334 of it, so they are up 6666.
        assertThat(balances.get(payer)).isEqualTo(6_666L);
        assertThat(balances.get(users.get(1).getId())).isEqualTo(-3_333L);
        assertThat(balances.get(users.get(2).getId())).isEqualTo(-3_333L);

        // The whole point: nothing was lost to rounding.
        assertThat(ledgerService.imbalanceOf(group.getId())).isZero();
    }

    @Test
    @DisplayName("settling up moves both parties toward zero")
    void settlementReducesBalances() {
        List<User> users = newUsers("Aditi", "Rohan");
        ExpenseGroup group = newGroup("Trip", users);
        Long aditi = users.get(0).getId();
        Long rohan = users.get(1).getId();

        expenseService.create(group.getId(), aditi, equalExpense("Hotel", "1000.00", aditi), null);
        assertThat(ledgerService.balanceOf(group.getId(), rohan)).isEqualTo(-50_000L);

        settlementService.record(group.getId(), rohan,
                new RecordSettlementRequest(rohan, aditi, new BigDecimal("500.00"), null), null);

        assertThat(ledgerService.balanceOf(group.getId(), rohan)).isZero();
        assertThat(ledgerService.balanceOf(group.getId(), aditi)).isZero();
        assertThat(ledgerService.imbalanceOf(group.getId())).isZero();
    }

    @Test
    @DisplayName("an exact split is recorded exactly as specified")
    void supportsExactSplits() {
        List<User> users = newUsers("Aditi", "Rohan");
        ExpenseGroup group = newGroup("Groceries", users);
        Long aditi = users.get(0).getId();
        Long rohan = users.get(1).getId();

        ExpenseView view = expenseService.create(group.getId(), aditi, new CreateExpenseRequest(
                "Groceries", new BigDecimal("250.00"), aditi, SplitType.EXACT, null, null,
                Map.of(aditi, new BigDecimal("100.00"), rohan, new BigDecimal("150.00"))), null);

        assertThat(view.splits()).hasSize(2);
        assertThat(ledgerService.balanceOf(group.getId(), rohan)).isEqualTo(-15_000L);
        assertThat(ledgerService.balanceOf(group.getId(), aditi)).isEqualTo(15_000L);
        assertThat(ledgerService.imbalanceOf(group.getId())).isZero();
    }

    @Test
    @DisplayName("a weighted split charges the couple double")
    void supportsWeightedSplits() {
        List<User> users = newUsers("Aditi", "Rohan");
        ExpenseGroup group = newGroup("Weekend", users);
        Long aditi = users.get(0).getId();
        Long rohan = users.get(1).getId();

        expenseService.create(group.getId(), aditi, new CreateExpenseRequest(
                "Cab", new BigDecimal("300.00"), aditi, SplitType.WEIGHTED, null,
                Map.of(aditi, 1, rohan, 2), null), null);

        // Aditi paid 300 and owes 100 => +200. Rohan owes 200.
        assertThat(ledgerService.balanceOf(group.getId(), aditi)).isEqualTo(20_000L);
        assertThat(ledgerService.balanceOf(group.getId(), rohan)).isEqualTo(-20_000L);
        assertThat(ledgerService.imbalanceOf(group.getId())).isZero();
    }

    @Test
    @DisplayName("balances stay correct across a long mixed history")
    void staysBalancedAcrossManyOperations() {
        List<User> users = newUsers("Aditi", "Rohan", "Sana", "Vikram");
        ExpenseGroup group = newGroup("Goa", users);

        for (int i = 0; i < 25; i++) {
            Long payer = users.get(i % users.size()).getId();
            expenseService.create(group.getId(), payer,
                    equalExpense("Expense " + i, (100 + i) + ".33", payer), null);
        }
        settlementService.record(group.getId(), users.get(1).getId(),
                new RecordSettlementRequest(users.get(1).getId(), users.get(0).getId(),
                        new BigDecimal("123.45"), null), null);

        assertThat(ledgerService.imbalanceOf(group.getId())).isZero();

        var balances = groupService.balances(group.getId(), users.get(0).getId());
        assertThat(balances.balances().stream().mapToLong(b -> b.balancePaise()).sum()).isZero();
    }

    @Test
    @DisplayName("the ledger refuses to accept an unbalanced batch")
    void rejectsUnbalancedLedgerWrites() {
        List<User> users = newUsers("Aditi", "Rohan");
        ExpenseGroup group = newGroup("Guard rail", users);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        ledgerService.append(group.getId(),
                                Map.of(users.get(0).getId(), 100L, users.get(1).getId(), -99L),
                                LedgerSource.EXPENSE, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unbalanced");
    }
}
