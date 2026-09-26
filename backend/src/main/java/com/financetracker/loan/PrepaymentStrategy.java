package com.financetracker.loan;

/**
 * How an extra principal payment changes the loan.
 * REDUCE_TENURE keeps the EMI constant and shortens the schedule.
 * REDUCE_EMI keeps the remaining tenure constant and lowers the EMI.
 */
public enum PrepaymentStrategy {
    REDUCE_TENURE,
    REDUCE_EMI
}
