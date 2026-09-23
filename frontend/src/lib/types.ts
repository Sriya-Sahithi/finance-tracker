export type Page<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type User = { id: number; email: string; name: string; createdAt: string };

export type Account = {
  id: number;
  name: string;
  type: "BANK" | "CASH" | "CREDIT_CARD" | "OTHER";
  openingBalance: string;
  currentBalance: string;
  currency: string;
  createdAt: string;
  updatedAt: string;
};

export type Category = { id: number; name: string; type: "INCOME" | "EXPENSE"; createdAt: string };

export type Transaction = {
  id: number;
  type: "INCOME" | "EXPENSE" | "TRANSFER" | "LOAN_PAYMENT";
  amount: string;
  transactionDate: string;
  description: string | null;
  notes: string | null;
  accountId: number;
  accountName: string;
  transferAccountId: number | null;
  transferAccountName: string | null;
  categoryId: number | null;
  categoryName: string | null;
  categoryType: "INCOME" | "EXPENSE" | null;
  loanId: number | null;
};

export type Budget = {
  id: number;
  categoryId: number;
  categoryName: string;
  year: number;
  month: number;
  amount: string;
  spent: string;
  remaining: string;
  usagePercent: string;
  overBudget: boolean;
};

export type Loan = {
  id: number;
  name: string;
  loanType: "HOME" | "PERSONAL" | "AUTO" | "EDUCATION" | "OTHER";
  principalAmount: string;
  outstandingPrincipal: string;
  annualInterestRate: string;
  tenureMonths: number;
  emiAmount: string;
  startDate: string;
  firstPaymentDate: string;
  paymentDueDay: number;
  prepaymentStrategy: string;
  remainingMonths: number;
  projectedPayoffDate: string | null;
};

export type ScheduleRow = {
  paymentNumber: number;
  date: string;
  openingPrincipal: string;
  emi: string;
  interest: string;
  principal: string;
  extraPrincipal: string;
  closingPrincipal: string;
  kind: string;
};

export type LoanPayment = {
  id: number;
  loanId: number;
  paymentDate: string;
  totalAmount: string;
  principalAmount: string;
  interestAmount: string;
  extraPrincipalAmount: string;
  remainingPrincipal: string;
  notes: string | null;
  transactionId: number | null;
  accountId: number | null;
};

export type Prepayment = {
  payment: LoanPayment;
  previousOutstanding: string;
  newOutstanding: string;
  interestSaved: string;
  previousPayoffDate: string;
  newPayoffDate: string;
  emisReduced: number;
  previousRemainingMonths: number;
  newRemainingMonths: number;
  strategy: string;
};

export type Dashboard = {
  year: number;
  month: number;
  income: string;
  expenses: string;
  savings: string;
  totalBudget: string;
  budgetUsed: string;
  budgetRemaining: string;
  loans: {
    totalOutstanding: string;
    totalEmiObligation: string;
    upcomingEmi: string;
    activeLoanCount: number;
    upcomingPayments: {
      loanId: number;
      loanName: string;
      dueDate: string;
      emiAmount: string;
      outstandingPrincipal: string;
    }[];
  };
};

export type MonthlyReport = {
  year: number;
  month: number;
  cashFlow: { year: number; month: number; income: string; expenses: string; savings: string }[];
  expenseByCategory: { categoryId: number; categoryName: string; amount: string }[];
  monthlySpending: { year: number; month: number; amount: string }[];
  budgetUtilization: {
    categoryId: number;
    categoryName: string;
    budget: string;
    spent: string;
    remaining: string;
    usagePercent: string;
    overBudget: boolean;
  }[];
  loanBalanceOverTime: { year: number; month: number; amount: string }[];
  interestVsPrincipal: { year: number; month: number; interest: string; principal: string }[];
};

export type ApiFieldError = { field: string; message: string };
