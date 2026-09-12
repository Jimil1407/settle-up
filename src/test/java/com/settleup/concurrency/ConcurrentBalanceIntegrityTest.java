package com.settleup.concurrency;

import com.settleup.expense.ExpenseRepository;
import com.settleup.expense.ExpenseService;
import com.settleup.group.ExpenseGroup;
import com.settleup.ledger.LedgerService;
import com.settleup.settlement.SettlementRepository;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The test this project exists to be able to write.
 *
 * <p>Everything else — the append-only ledger, paise arithmetic, the group row lock — is machinery
 * in service of one claim: <b>concurrent writes cannot make a group's money wrong</b>. These tests
 * try hard to break that claim by hammering a single group from many threads at once, and then
 * assert the invariant that would be violated if any update were lost, double-applied or
 * interleaved.
 *
 * <p>A {@link CountDownLatch} is used as a starting gun so the threads genuinely collide, rather
 * than trickling in as they are scheduled and accidentally serialising themselves.
 */
class ConcurrentBalanceIntegrityTest extends IntegrationTestBase {

    private static final int THREADS = 50;
    private static final int OPERATIONS = 200;

    @Autowired
    private ExpenseService expenseService;
    @Autowired
    private SettlementService settlementService;
    @Autowired
    private LedgerService ledgerService;
    @Autowired
    private ExpenseRepository expenseRepository;
    @Autowired
    private SettlementRepository settlementRepository;

    @Test
    @DisplayName("200 concurrent expenses on one group: no lost updates, balances still net to zero")
    void concurrentExpensesNeverCorruptBalances() throws Exception {
        List<User> users = newUsers("Aditi", "Rohan", "Sana", "Vikram", "Meera");
        ExpenseGroup group = newGroup("Flat 402", users);

        // 99.99 over 5 people is deliberately indivisible: 9999 = 2000*4 + 1999. If any rounding
        // were sloppy, 200 repetitions would amplify it into an obvious drift.
        AtomicInteger index = new AtomicInteger();
        List<Future<?>> results = runConcurrently(OPERATIONS, () -> {
            int i = index.getAndIncrement();
            Long payer = users.get(i % users.size()).getId();
            return expenseService.create(group.getId(), payer,
                    equalExpense("Expense " + i, "99.99", payer), null);
        });

        assertAllSucceeded(results);

        assertThat(expenseRepository.findByGroupIdOrderByCreatedAtDescIdDesc(group.getId()))
                .as("every submitted expense must be persisted exactly once")
                .hasSize(OPERATIONS);

        // The invariant. If two transactions had interleaved and lost an update, this would be
        // non-zero and every balance in the group would be untrustworthy.
        assertThat(ledgerService.imbalanceOf(group.getId()))
                .as("group ledger must sum to exactly zero after %d concurrent writes", OPERATIONS)
                .isZero();

        // Each of the 5 users paid 40 times. Shares are 2000 paise for the four lowest ids and
        // 1999 for the highest, so:
        //   lowest four : 40*9999 - 200*2000 = -40 paise
        //   highest     : 40*9999 - 200*1999 = +160 paise
        Map<Long, Long> balances = ledgerService.netBalances(group.getId());
        for (int i = 0; i < 4; i++) {
            assertThat(balances.get(users.get(i).getId()))
                    .as("balance for user %d", i)
                    .isEqualTo(-40L);
        }
        assertThat(balances.get(users.get(4).getId())).isEqualTo(160L);
    }

    @Test
    @DisplayName("expenses and settlements racing on the same group stay consistent")
    void mixedConcurrentWritesStayConsistent() throws Exception {
        List<User> users = newUsers("Aditi", "Rohan", "Sana", "Vikram");
        ExpenseGroup group = newGroup("Goa trip", users);
        Long aditi = users.get(0).getId();
        Long rohan = users.get(1).getId();

        // Seed a debt so the settlements below have something to pay down.
        expenseService.create(group.getId(), aditi, equalExpense("Villa", "40000.00", aditi), null);

        AtomicInteger index = new AtomicInteger();
        List<Future<?>> results = runConcurrently(OPERATIONS, () -> {
            int i = index.getAndIncrement();
            if (i % 2 == 0) {
                Long payer = users.get(i % users.size()).getId();
                return expenseService.create(group.getId(), payer,
                        equalExpense("Expense " + i, "250.50", payer), null);
            }
            // No expectedVersion: an ad-hoc payment, not one derived from a plan, so it is not
            // subject to stale-plan rejection.
            return settlementService.record(group.getId(), rohan,
                    new RecordSettlementRequest(rohan, aditi, new BigDecimal("10.00"), null), null);
        });

        assertAllSucceeded(results);

        assertThat(ledgerService.imbalanceOf(group.getId()))
                .as("ledger must stay balanced across interleaved expenses and settlements")
                .isZero();
        assertThat(settlementRepository.findByGroupIdOrderByCreatedAtDescIdDesc(group.getId()))
                .hasSize(OPERATIONS / 2);
    }

    @Test
    @DisplayName("the group version advances once per balance-changing write, with no gaps")
    void versionAdvancesExactlyOncePerWrite() throws Exception {
        List<User> users = newUsers("Aditi", "Rohan", "Sana");
        ExpenseGroup group = newGroup("Version check", users);
        long startingVersion = groupRepository.findById(group.getId()).orElseThrow().getVersion();

        int writes = 60;
        AtomicInteger index = new AtomicInteger();
        assertAllSucceeded(runConcurrently(writes, () -> {
            int i = index.getAndIncrement();
            Long payer = users.get(i % users.size()).getId();
            return expenseService.create(group.getId(), payer,
                    equalExpense("Expense " + i, "10.00", payer), null);
        }));

        long finalVersion = groupRepository.findById(group.getId()).orElseThrow().getVersion();

        // Exactly one bump per write proves no write silently skipped the locking protocol, and
        // that none of them clobbered another's increment.
        assertThat(finalVersion - startingVersion).isEqualTo(writes);
        assertThat(ledgerService.imbalanceOf(group.getId())).isZero();
    }

    /**
     * The "I tapped Add twice on a bad connection" case, amplified: many identical requests all
     * carrying the same idempotency key must produce exactly one expense.
     */
    @Test
    @DisplayName("duplicate requests sharing an idempotency key create exactly one expense")
    void idempotencyKeyPreventsDoubleCharging() throws Exception {
        List<User> users = newUsers("Aditi", "Rohan");
        ExpenseGroup group = newGroup("Idempotency", users);
        Long aditi = users.get(0).getId();
        String key = "duplicate-tap-" + System.nanoTime();

        int attempts = 40;
        List<Future<?>> results = runConcurrently(attempts, () ->
                expenseService.create(group.getId(), aditi,
                        equalExpense("Dinner", "500.00", aditi), key));

        // Losers of the race are rejected as in-flight duplicates rather than silently queued,
        // so some attempts are expected to fail. What matters is what reached the database.
        int succeeded = 0;
        for (Future<?> future : results) {
            try {
                future.get();
                succeeded++;
            } catch (Exception ignored) {
                // expected for duplicate attempts
            }
        }
        assertThat(succeeded).isPositive();

        assertThat(expenseRepository.findByGroupIdOrderByCreatedAtDescIdDesc(group.getId()))
                .as("%d identical requests must yield exactly one expense", attempts)
                .hasSize(1);
        assertThat(ledgerService.balanceOf(group.getId(), aditi)).isEqualTo(25_000L);
        assertThat(ledgerService.imbalanceOf(group.getId())).isZero();
    }

    /**
     * Fires {@code count} tasks at once and waits for all of them.
     */
    private List<Future<?>> runConcurrently(int count, Callable<?> task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(count);
        List<Future<?>> futures = new java.util.ArrayList<>(count);

        try {
            for (int i = 0; i < count; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    // Hold every thread here so they are released simultaneously and actually
                    // contend, instead of drifting apart as the pool schedules them.
                    startGun.await(30, TimeUnit.SECONDS);
                    return task.call();
                }));
            }
            ready.await(30, TimeUnit.SECONDS);
            startGun.countDown();

            for (Future<?> future : futures) {
                try {
                    future.get(120, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                    // Individual outcomes are asserted by the caller.
                }
            }
            return futures;
        } finally {
            pool.shutdownNow();
        }
    }

    private static void assertAllSucceeded(List<Future<?>> futures) {
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception ex) {
                throw new AssertionError(
                        "a concurrent operation failed, which means contention was not handled: "
                                + rootCause(ex), ex);
            }
        }
    }

    private static String rootCause(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.toString();
    }
}
