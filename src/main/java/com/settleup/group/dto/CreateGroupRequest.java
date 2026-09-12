package com.settleup.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateGroupRequest(
        @NotBlank @Size(min = 1, max = 120) String name,
        @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO code") String currency) {

    public String currencyOrDefault() {
        return currency == null || currency.isBlank() ? "INR" : currency;
    }
}
