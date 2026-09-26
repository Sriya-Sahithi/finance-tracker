export type Dashboard = {
  year: number;
  month: number;
  income: string;
  expenses: string;
  savings: string;
  totalBudget: string;
  budgetUsed: string;
  budgetRemaining: string;
  totalAccountBalance: string;
  accountBalances: {
    id: number;
    name: string;
    type: string;
    balance: string;
  }[];
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
