package com.financetracker.account.dto;

import com.financetracker.account.Account;
import com.financetracker.account.AccountType;
import com.financetracker.common.money.Money;
import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
        Long id,
        String name,
        AccountType type,
        BigDecimal openingBalance,
        BigDecimal currentBalance,
        String currency,
        Instant createdAt,
        Instant updatedAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getType(),
                Money.scale(account.getOpeningBalance()),
                Money.scale(account.getCurrentBalance()),
                account.getCurrency(),
                account.getCreatedAt(),
                account.getUpdatedAt());
    }
}
