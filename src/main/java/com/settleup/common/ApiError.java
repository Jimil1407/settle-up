package com.settleup.common;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Uniform error body so clients never have to parse a stack trace or an HTML page. */
public record ApiError(
        int status,
        String error,
        String message,
        Instant timestamp,
        List<String> details,
        Map<String, Object> meta) {

    public static ApiError of(int status, String error, String message) {
        return new ApiError(status, error, message, Instant.now(), List.of(), Map.of());
    }

    public static ApiError of(int status, String error, String message, List<String> details) {
        return new ApiError(status, error, message, Instant.now(), details, Map.of());
    }

    public static ApiError of(int status, String error, String message, Map<String, Object> meta) {
        return new ApiError(status, error, message, Instant.now(), List.of(), meta);
    }
}
