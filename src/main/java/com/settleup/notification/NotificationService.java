package com.settleup.notification;

import com.settleup.common.Money;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Fan-out of "something happened in your group" messages.
 *
 * <p>Everything here runs on the bounded {@code backgroundExecutor} rather than the request thread:
 * pushing a notification is not something the user should wait for, and a slow or failing provider
 * must never turn into a slow or failing "add expense" call.
 *
 * <p>In a real deployment these methods would hand off to FCM, SMS or email. The delivery mechanism
 * is stubbed, but the threading, isolation and failure semantics around it are real.
 */
@Service
@Slf4j
public class NotificationService {

    @Async("backgroundExecutor")
    public void expenseAdded(Long groupId, Long expenseId, String description, long totalPaise) {
        safely(() -> log.info("[notify] group={} expense={} \"{}\" for {} added",
                groupId, expenseId, description, Money.format(totalPaise)));
    }

    @Async("backgroundExecutor")
    public void settlementRecorded(Long groupId, Long fromUserId, Long toUserId, long amountPaise) {
        safely(() -> log.info("[notify] group={} user {} paid user {} {}",
                groupId, fromUserId, toUserId, Money.format(amountPaise)));
    }

    @Async("backgroundExecutor")
    public void recurringExpenseGenerated(Long groupId, Long expenseId, String description) {
        safely(() -> log.info("[notify] group={} recurring expense {} \"{}\" generated",
                groupId, expenseId, description));
    }

    /**
     * An exception thrown inside an {@code @Async void} method is swallowed by the executor and
     * never surfaces anywhere useful, so we catch and log it here instead of losing it silently.
     */
    private void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            log.warn("notification delivery failed: {}", ex.toString(), ex);
        }
    }
}
