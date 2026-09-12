package com.settleup.export;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RestController
@RequestMapping("/api/groups/{groupId}/export")
@RequiredArgsConstructor
public class ExportController {

    private final CsvExportService csvExportService;

    @GetMapping(value = "/expenses.csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportExpenses(@PathVariable Long groupId,
                                                 @AuthenticationPrincipal Long userId)
            throws ExecutionException, InterruptedException, TimeoutException {
        // Bounded wait: the work happens on the background pool, and a pathological export gives up
        // rather than pinning an HTTP thread indefinitely.
        String csv = csvExportService.exportExpenses(groupId, userId).get(30, TimeUnit.SECONDS);
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"group-" + groupId + "-expenses.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(body);
    }
}
