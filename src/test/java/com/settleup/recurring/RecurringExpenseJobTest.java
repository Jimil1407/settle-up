package com.settleup.recurring;

import com.settleup.expense.ExpenseRepository;
import com.settleup.expense.SplitType;
import com.settleup.group.ExpenseGroup;
import com.settleup.ledger.LedgerService;
import com.settleup.support.IntegrationTestBase;
import com.settleup.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rent job. Its whole reason for existing is that it must be safe to run more than once —
 * restarts, overlapping schedules and a second app instance are all normal, and none of them may
 * result in anyone being charged rent twice.
 */
class RecurringExpenseJobTest extends IntegrationTestBase {

    @Autowired
    private RecurringExpenseJob job;
    @Autowired
    private RecurringExpenseTemplateRepository templateRepository;
    @Autowired
    private ExpenseRepository expenseRepository;
    @Autowired
    private LedgerService ledgerService;

    /**
     * The job deliberately processes every active template in the system, and the H2 database is
     * shared across the suite for speed. Retiring pre-existing templates first keeps each test's
     * assertions about the job's return value scoped to the template that test created.
     */
    @BeforeEach
    void retirePreExistingTemplates() {
        List<RecurringExpenseTemplate> active = templateRepository.findByActiveTrue();
        active.forEach(template -> template.setActive(false));
        templateRepository.saveAll(active);
    }

    @Test
    @DisplayName("rent is generated once and split across the flat")
    void generatesRentForTheMonth() {
        List<User> users = newUsers("Aditi", "Rohan", "Sana");
        ExpenseGroup group = newGroup("Flat 402", users);
        newTemplate(group, users.get(0), "Rent", 3_000_000L, 1);

        int generated = job.generateFor(LocalDate.of(2026, 9, 15));

        assertThat(generated).isEqualTo(1);
        var expenses = expenseRepository.findByGroupIdOrderByCreatedAtDescIdDesc(group.getId());
        assertThat(expenses).hasSize(1);
        assertThat(expenses.get(0).getDescription()).isEqualTo("Rent");
        assertThat(expenses.get(0).getPeriodKey()).isEqualTo("2026-09");

        // Aditi fronted 30000 and owes a third of it.
        assertThat(ledgerService.balanceOf(group.getId(), users.get(0).getId())).isEqualTo(2_000_000L);
        assertThat(ledgerService.imbalanceOf(group.getId())).isZero();
    }

    @Test
    @DisplayName("running the job repeatedly in the same month does not charge rent twice")
    void isIdempotentWithinAPeriod() {
        List<User> users = newUsers("Aditi", "Rohan");
        ExpenseGroup group = newGroup("Idempotent rent", users);
        newTemplate(group, users.get(0), "Rent", 2_000_000L, 1);

        LocalDate someDayInSeptember = LocalDate.of(2026, 9, 20);
        assertThat(job.generateFor(someDayInSeptember)).isEqualTo(1);

        // Simulate a restart, a retry and an overlapping schedule.
        assertThat(job.generateFor(someDayInSeptember)).isZero();
        assertThat(job.generateFor(LocalDate.of(2026, 9, 25))).isZero();
        assertThat(job.generateFor(LocalDate.of(2026, 9, 30))).isZero();

        assertThat(expenseRepository.findByGroupIdOrderByCreatedAtDescIdDesc(group.getId())).hasSize(1);
        assertThat(ledgerService.imbalanceOf(group.getId())).isZero();
    }

    @Test
    @DisplayName("a new month produces a new charge")
    void generatesAgainInTheNextPeriod() {
        List<User> users = newUsers("Aditi", "Rohan");
        ExpenseGroup group = newGroup("Monthly rent", users);
        newTemplate(group, users.get(0), "Rent", 1_000_000L, 1);

        job.generateFor(LocalDate.of(2026, 9, 5));
        job.generateFor(LocalDate.of(2026, 10, 5));

        var expenses = expenseRepository.findByGroupIdOrderByCreatedAtDescIdDesc(group.getId());
        assertThat(expenses).hasSize(2);
        assertThat(expenses).extracting("periodKey").containsExactlyInAnyOrder("2026-09", "2026-10");
    }

    @Test
    @DisplayName("nothing is charged before the template's day of the month")
    void waitsUntilTheChargeDay() {
        List<User> users = newUsers("Aditi", "Rohan");
        ExpenseGroup group = newGroup("Later rent", users);
        newTemplate(group, users.get(0), "Rent", 1_000_000L, 15);

        assertThat(job.generateFor(LocalDate.of(2026, 9, 10))).isZero();
        assertThat(job.generateFor(LocalDate.of(2026, 9, 15))).isEqualTo(1);
    }

    @Test
    @DisplayName("a template set to the 31st still fires in February")
    void clampsChargeDayToShortMonths() {
        List<User> users = newUsers("Aditi", "Rohan");
        ExpenseGroup group = newGroup("Short month", users);
        newTemplate(group, users.get(0), "Rent", 1_000_000L, 31);

        // February 2026 has 28 days, so the 31st is clamped to the 28th rather than never arriving.
        assertThat(job.generateFor(LocalDate.of(2026, 2, 28))).isEqualTo(1);
    }

    @Test
    @DisplayName("a deactivated template stops charging")
    void skipsInactiveTemplates() {
        List<User> users = newUsers("Aditi", "Rohan");
        ExpenseGroup group = newGroup("Cancelled", users);
        RecurringExpenseTemplate template = newTemplate(group, users.get(0), "Netflix", 64_900L, 1);

        template.setActive(false);
        templateRepository.save(template);

        assertThat(job.generateFor(LocalDate.of(2026, 9, 10))).isZero();
        assertThat(expenseRepository.findByGroupIdOrderByCreatedAtDescIdDesc(group.getId())).isEmpty();
    }

    private RecurringExpenseTemplate newTemplate(ExpenseGroup group, User payer, String description,
                                                 long totalPaise, int dayOfMonth) {
        return templateRepository.save(RecurringExpenseTemplate.builder()
                .groupId(group.getId())
                .description(description)
                .totalPaise(totalPaise)
                .paidByUserId(payer.getId())
                .splitType(SplitType.EQUAL)
                .dayOfMonth(dayOfMonth)
                .active(true)
                .createdAt(Instant.now())
                .build());
    }
}
