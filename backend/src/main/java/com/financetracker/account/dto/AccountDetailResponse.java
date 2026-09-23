package com.financetracker.account.dto;

import com.financetracker.transaction.dto.TransactionResponse;
import java.util.List;

public record AccountDetailResponse(AccountResponse account, List<TransactionResponse> recentTransactions) {
}
