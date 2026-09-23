package com.financetracker.transaction;

import com.financetracker.account.Account;
import com.financetracker.common.money.Money;
import java.math.BigDecimal;

/**
 * Applies income, expense, transfer, and loan-payment effects to account balances.
 * Transfers move money between two accounts and are not treated as income or expense.
 */
public final class AccountLedger {

    private AccountLedger() {
    }

    public static void apply(TransactionType type, BigDecimal amount, Account account, Account transferAccount) {
        change(type, amount, account, transferAccount, false);
    }

    public static void reverse(TransactionType type, BigDecimal amount, Account account, Account transferAccount) {
        change(type, amount, account, transferAccount, true);
    }

    private static void change(
            TransactionType type,
            BigDecimal amount,
            Account account,
            Account transferAccount,
            boolean reverse
    ) {
        BigDecimal signed = reverse ? amount.negate() : amount;
        switch (type) {
            case INCOME -> add(account, signed);
            case EXPENSE, LOAN_PAYMENT -> add(account, signed.negate());
            case TRANSFER -> {
                if (transferAccount == null) {
                    throw new IllegalArgumentException("Transfer requires a destination account");
                }
                add(account, signed.negate());
                add(transferAccount, signed);
            }
        }
    }

    private static void add(Account account, BigDecimal delta) {
        account.setCurrentBalance(Money.scale(account.getCurrentBalance().add(delta)));
    }
}
