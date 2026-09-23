package com.financetracker.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.financetracker.account.Account;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AccountLedgerTest {

    @Test
    void incomeIncreasesBalanceAndExpenseDecreasesIt() {
        Account cash = account("1000.00");
        AccountLedger.apply(TransactionType.INCOME, new BigDecimal("250.50"), cash, null);
        assertThat(cash.getCurrentBalance()).isEqualByComparingTo("1250.50");

        AccountLedger.apply(TransactionType.EXPENSE, new BigDecimal("50.25"), cash, null);
        assertThat(cash.getCurrentBalance()).isEqualByComparingTo("1200.25");

        AccountLedger.reverse(TransactionType.EXPENSE, new BigDecimal("50.25"), cash, null);
        assertThat(cash.getCurrentBalance()).isEqualByComparingTo("1250.50");
    }

    @Test
    void transferMovesMoneyWithoutCreatingIncomeOrExpense() {
        Account bank = account("5000.00");
        Account cash = account("100.00");

        AccountLedger.apply(TransactionType.TRANSFER, new BigDecimal("400.00"), bank, cash);

        assertThat(bank.getCurrentBalance()).isEqualByComparingTo("4600.00");
        assertThat(cash.getCurrentBalance()).isEqualByComparingTo("500.00");
        assertThat(bank.getCurrentBalance().add(cash.getCurrentBalance())).isEqualByComparingTo("5100.00");
    }

    @Test
    void loanPaymentReducesOnlyTheSourceAccount() {
        Account bank = account("2000.00");
        AccountLedger.apply(TransactionType.LOAN_PAYMENT, new BigDecimal("8791.59"), bank, null);
        assertThat(bank.getCurrentBalance()).isEqualByComparingTo("-6791.59");
    }

    private static Account account(String balance) {
        Account account = new Account();
        account.setOpeningBalance(new BigDecimal(balance));
        account.setCurrentBalance(new BigDecimal(balance));
        return account;
    }
}
