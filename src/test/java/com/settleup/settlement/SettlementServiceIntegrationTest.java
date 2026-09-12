package com.settleup.settlement;

import com.settleup.common.StalePlanException;
import com.settleup.expense.ExpenseService;
import com.settleup.group.ExpenseGroup;
import com.settleup.ledger.LedgerService;
import com.settleup.settlement.dto.RecordSettlementRequest;
import com.settleup.settlement.dto.SettlementPlanResponse;
import com.settleup.settlement.dto.SettlementView;
import com.settleup.support.IntegrationTestBase;
import com.settleup.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ExpenseService expenseService;
    @Autowired
    private SettlementService settlementService;
    @Autowired
    private LedgerService ledgerService;

    @Test
    @DisplayName("a tangle of debts collapses into a short payment plan that fully settles the group")
    void producesAPlanThatActuallySettlesTheGroup() {
        List<User> users = newUsers("Aditi", "Rohan", "Sana", "Vikram");
        ExpenseGroup group = newGroup("Goa trip", users);
        Long aditi = users.get(0).getId();
        Long rohan = users.get(1).getId();
        Long sana = users.get(2).getId();

        // A realistic trip: different people front different things.
        expenseService.create(group.getId(), aditi, equalExpense("Villa", "12000.00", aditi), null);
        expenseService.create(group.getId(), rohan, equalExpense("Cab", "3200.00", rohan), null);
        expenseService.create(group.getId(), sana, equalExpense("Dinner", "4800.00", sana), null);
        expenseService.create(group.getId(), aditi, equalExpense("Breakfast", "1600.00", aditi), null);

        SettlementPlanResponse plan = settlementService.plan(group.getId(), aditi);

        assertThat(plan.transfers()).isNotEmpty();
        assertThat(plan.simplifiedTransferCount())
                .as("at most n-1 payments for %d people", users.size())
                .isLessThanOrEqualTo(users.size() - 1);

        // Execute the plan exactly as the UI would, then confirm everybody is square.
        for (var transfer : plan.transfers()) {
            settlementService.record(group.getId(), transfer.fromUserId(),
                    new RecordSettlementRequest(transfer.fromUserId(), transfer.toUserId(),
                            transfer.amountRupees(), null), null);
        }

        assertThat(ledgerService.netBalances(group.getId()).values())
                .as("after executing the plan nobody owes anybody")
                .allSatisfy(balance -> assertThat(balance).isZero());
        assertThat(settlementService.plan(group.getId(), aditi).transfers()).isEmpty();
    }

    @Test
    @DisplayName("acting on a plan that was overtaken by a new expense is rejected with a conflict")
    void rejectsStaleSettlementPlans() {
        List<User> users = newUsers("Aditi", "Rohan");
        ExpenseGroup group = newGroup("Stale plan", users);
        Long aditi = users.get(0).getId();
        Long rohan = users.get(1).getId();

        expenseService.create(group.getId(), aditi, equalExpense("Hotel", "1000.00", aditi), null);

        // Rohan opens the settle-up screen...
        SettlementPlanResponse plan = settlementService.plan(group.getId(), rohan);
        long versionRohanSaw = plan.version();

        // ...and while it is on screen, Aditi adds another expense.
        expenseService.create(group.getId(), aditi, equalExpense("Dinner", "600.00", aditi), null);

        // Rohan taps "settle" against numbers that no longer reflect reality.
        assertThatThrownBy(() -> settlementService.record(group.getId(), rohan,
                new RecordSettlementRequest(rohan, aditi, new BigDecimal("500.00"), versionRohanSaw), null))
                .isInstanceOf(StalePlanException.class)
                .hasMessageContaining("re-fetch the plan");

        // Re-fetching gives a fresh version, and the same payment now goes through.
        SettlementPlanResponse fresh = settlementService.plan(group.getId(), rohan);
        assertThat(fresh.version()).isGreaterThan(versionRohanSaw);

        SettlementView recorded = settlementService.record(group.getId(), rohan,
                new RecordSettlementRequest(rohan, aditi, new BigDecimal("500.00"), fresh.version()), null);
        assertThat(recorded.amountPaise()).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("an ad-hoc payment with no plan version attached is always allowed")
    void allowsSettlementsWithoutAVersion() {
        List<User> users = newUsers("Aditi", "Rohan");
        ExpenseGroup group = newGroup("Ad hoc", users);
        Long aditi = users.get(0).getId();
        Long rohan = users.get(1).getId();

        expenseService.create(group.getId(), aditi, equalExpense("Hotel", "1000.00", aditi), null);

        SettlementView view = settlementService.record(group.getId(), rohan,
                new RecordSettlementRequest(rohan, aditi, new BigDecimal("200.00"), null), null);

        assertThat(view.amountPaise()).isEqualTo(20_000L);
        assertThat(ledgerService.imbalanceOf(group.getId())).isZero();
    }

    @Test
    @DisplayName("replaying a settlement with the same idempotency key returns the original")
    void settlementIsIdempotent() {
        List<User> users = newUsers("Aditi", "Rohan");
        ExpenseGroup group = newGroup("Idempotent settle", users);
        Long aditi = users.get(0).getId();
        Long rohan = users.get(1).getId();
        String key = "settle-key-" + System.nanoTime();

        expenseService.create(group.getId(), aditi, equalExpense("Hotel", "1000.00", aditi), null);

        SettlementView first = settlementService.record(group.getId(), rohan,
                new RecordSettlementRequest(rohan, aditi, new BigDecimal("500.00"), null), key);
        SettlementView replay = settlementService.record(group.getId(), rohan,
                new RecordSettlementRequest(rohan, aditi, new BigDecimal("500.00"), null), key);

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(settlementService.list(group.getId(), aditi)).hasSize(1);
        // Rohan owed 500 and paid 500 once, not twice.
        assertThat(ledgerService.balanceOf(group.getId(), rohan)).isZero();
    }

    @Test
    @DisplayName("a group with nothing outstanding produces an empty plan")
    void emptyGroupNeedsNoPayments() {
        List<User> users = newUsers("Aditi", "Rohan");
        ExpenseGroup group = newGroup("Nothing owed", users);

        SettlementPlanResponse plan = settlementService.plan(group.getId(), users.get(0).getId());

        assertThat(plan.transfers()).isEmpty();
        assertThat(plan.simplifiedTransferCount()).isZero();
    }

    @Test
    @DisplayName("you cannot settle up with yourself")
    void rejectsSelfSettlement() {
        List<User> users = newUsers("Aditi", "Rohan");
        ExpenseGroup group = newGroup("Self settle", users);
        Long aditi = users.get(0).getId();

        assertThatThrownBy(() -> settlementService.record(group.getId(), aditi,
                new RecordSettlementRequest(aditi, aditi, new BigDecimal("100.00"), null), null))
                .isInstanceOf(com.settleup.common.ValidationException.class)
                .hasMessageContaining("yourself");
    }

    @Test
    @DisplayName("simplification beats paying every debt individually")
    void simplificationReducesPaymentCount() {
        List<User> users = newUsers("Aditi", "Rohan", "Sana", "Vikram", "Meera");
        ExpenseGroup group = newGroup("Big group", users);

        // Ten expenses with rotating payers produce a messy web of who-owes-whom.
        for (int i = 0; i < 10; i++) {
            Long payer = users.get(i % users.size()).getId();
            expenseService.create(group.getId(), payer,
                    equalExpense("Expense " + i, (500 + i * 37) + ".77", payer), null);
        }

        SettlementPlanResponse plan = settlementService.plan(group.getId(), users.get(0).getId());

        assertThat(plan.simplifiedTransferCount()).isLessThanOrEqualTo(users.size() - 1);
        assertThat(plan.simplifiedTransferCount()).isLessThan(plan.rawDebtCount());
    }
}
