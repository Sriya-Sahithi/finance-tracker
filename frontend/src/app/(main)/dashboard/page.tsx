"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { dashboardApi } from "@/lib/api";
import { formatDate, formatInr, MONTHS } from "@/lib/format";
import type { Dashboard } from "@/lib/types";
import { MonthPicker, usePeriod } from "@/components/month-picker";
import { ErrorText, Stat } from "@/components/feedback";

export default function DashboardPage() {
  const { year, month } = usePeriod();
  const [data, setData] = useState<Dashboard | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    dashboardApi.get(year, month).then(setData).catch((err) => setError(err.message));
  }, [year, month]);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Dashboard</h1>
          <p className="text-sm text-muted-foreground">{MONTHS[month - 1]} {year} · Asia/Kolkata</p>
        </div>
        <MonthPicker />
      </div>
      <ErrorText message={error} />
      {data && (
        <>
          <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Stat label="Income" value={formatInr(data.income)} />
            <Stat label="Expenses" value={formatInr(data.expenses)} />
            <Stat label="Savings" value={formatInr(data.savings)} hint="Income minus expenses" />
            <Stat label="Budget used" value={formatInr(data.budgetUsed)} hint={`of ${formatInr(data.totalBudget)}`} />
          </section>
          <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Stat label="Outstanding loans" value={formatInr(data.loans.totalOutstanding)} hint={`${data.loans.activeLoanCount} active`} />
            <Stat label="EMI obligation" value={formatInr(data.loans.totalEmiObligation)} />
            <Stat label="Upcoming EMI" value={formatInr(data.loans.upcomingEmi)} />
            <Stat label="Budget remaining" value={formatInr(data.budgetRemaining)} />
          </section>
          <section className="rounded-lg border bg-white">
            <div className="border-b px-5 py-4">
              <h2 className="font-semibold">Upcoming loan EMIs</h2>
            </div>
            {data.loans.upcomingPayments.length === 0 ? (
              <p className="px-5 py-8 text-sm text-muted-foreground">No EMIs are due in this month.</p>
            ) : (
              <ul>
                {data.loans.upcomingPayments.map((payment) => (
                  <li key={payment.loanId} className="flex items-center justify-between gap-3 border-t px-5 py-3 text-sm first:border-t-0">
                    <div>
                      <Link className="font-medium hover:underline" href={`/loans/${payment.loanId}`}>{payment.loanName}</Link>
                      <p className="text-muted-foreground">Due {formatDate(payment.dueDate)}</p>
                    </div>
                    <span className="tabular font-medium">{formatInr(payment.emiAmount)}</span>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </>
      )}
    </div>
  );
}
