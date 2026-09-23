package com.financetracker.loan;

/**
 * How an extra principal payment changes the loan.
 * REDUCE_TENURE keeps the EMI constant and shortens the schedule.
 * REDUCE_EMI can be added later as another constant without changing controllers.
 */
public enum PrepaymentStrategy {
    REDUCE_TENURE
}
