package com.settleup.recurring;

import com.settleup.expense.ExpenseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Materialises standing expenses (rent, the shared Netflix plan) once per month.
 *
 * <p>The job itself is intentionally dumb: it makes no attempt to run exactly once, because that
 * is a guarantee no scheduler can actually provide. Restarts, clock changes and a second instance
 * of the app would all break an "exactly once" assumption. Instead every generation is keyed by
 * {@code (templateId, period)} and enforced by a unique index, so running this hourly, daily, or
 * twenty times in a row all produce the same result.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecurringExpenseJob {

    private final RecurringExpenseTemplateRepository templateRepository;
    private final ExpenseService expenseService;

    /** Runs at 02:00 daily; templates due earlier in the month are caught up on the next run. */
    @Scheduled(cron = "${settleup.recurring.cron:0 0 2 * * *}")
    public void generateDueExpenses() {
        generateFor(LocalDate.now());
    }

    /**
     * Package-visible seam so tests can drive a specific date instead of waiting for the clock.
     *
     * @return how many expenses were actually created
     */
    public int generateFor(LocalDate today) {
        List<RecurringExpenseTemplate> templates = templateRepository.findByActiveTrue();
        String periodKey = YearMonth.from(today).toString();
        int generated = 0;

        for (RecurringExpenseTemplate template : templates) {
            if (!isDue(template, today)) {
                continue;
            }
            try {
                var expenseId = expenseService.generateRecurring(
                        template.getGroupId(),
                        template.getId(),
                        periodKey,
                        template.getDescription(),
                        template.getTotalPaise(),
                        template.getPaidByUserId(),
                        template.getSplitType());
                if (expenseId.isPresent()) {
                    generated++;
                }
            } catch (RuntimeException ex) {
                // One bad template must not stop the rest of the batch from being generated.
                log.error("failed to generate recurring template {} for period {}",
                        template.getId(), periodKey, ex);
            }
        }

        if (generated > 0) {
            log.info("generated {} recurring expenses for period {}", generated, periodKey);
        }
        return generated;
    }

    /**
     * A template is due once the month has reached its charge day. The day is clamped to the length
     * of the month, so a template set to the 31st still fires in February rather than being
     * skipped for the shorter months.
     */
    private static boolean isDue(RecurringExpenseTemplate template, LocalDate today) {
        int effectiveDay = Math.min(template.getDayOfMonth(), today.lengthOfMonth());
        return today.getDayOfMonth() >= effectiveDay;
    }
}
