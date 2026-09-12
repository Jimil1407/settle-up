package com.settleup.export;

import com.settleup.common.Money;
import com.settleup.expense.dto.ExpenseView;
import com.settleup.expense.ExpenseService;
import com.settleup.group.GroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Builds a CSV of a group's expense history.
 *
 * <p>Runs on the background pool and returns a {@link CompletableFuture}, so a large export cannot
 * occupy an HTTP worker thread while it churns. The controller waits on the future with a timeout,
 * which keeps the endpoint synchronous from the client's point of view while still bounding how
 * long a request can tie up server resources.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CsvExportService {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final ExpenseService expenseService;
    private final GroupService groupService;

    @Async("backgroundExecutor")
    public CompletableFuture<String> exportExpenses(Long groupId, Long actorUserId) {
        groupService.requireMember(groupId, actorUserId);
        List<ExpenseView> expenses = expenseService.list(groupId, actorUserId);

        StringBuilder csv = new StringBuilder("Date,Description,Paid By,Total,Split Type,Participant,Share\n");
        for (ExpenseView e : expenses) {
            for (var split : e.splits()) {
                csv.append(escape(DATE.format(e.createdAt()))).append(',')
                        .append(escape(e.description())).append(',')
                        .append(escape(e.paidByName())).append(',')
                        .append(Money.paiseToRupees(e.totalPaise())).append(',')
                        .append(e.splitType()).append(',')
                        .append(escape(split.displayName())).append(',')
                        .append(Money.paiseToRupees(split.sharePaise()))
                        .append('\n');
            }
        }
        log.info("exported {} expenses for group {}", expenses.size(), groupId);
        return CompletableFuture.completedFuture(csv.toString());
    }

    /**
     * RFC 4180 quoting. Without this a description like {@code Dinner, drinks} would silently shift
     * every later column in the row.
     */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
