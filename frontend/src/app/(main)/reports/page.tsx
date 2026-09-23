"use client";

import { useEffect, useState } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { reportApi } from "@/lib/api";
import { MONTHS } from "@/lib/format";
import type { MonthlyReport } from "@/lib/types";
import { MonthPicker, usePeriod } from "@/components/month-picker";
import { ErrorText } from "@/components/feedback";

const COLORS = ["#0f766e", "#115e59", "#0ea5e9", "#f59e0b", "#e11d48", "#6366f1", "#84cc16", "#64748b"];

export default function ReportsPage() {
  const { year, month } = usePeriod();
  const [report, setReport] = useState<MonthlyReport | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    reportApi.monthly(year, month).then(setReport).catch((err) => setError(err.message));
  }, [year, month]);

  const cashFlow = report?.cashFlow.map((point) => ({
    name: MONTHS[point.month - 1].slice(0, 3),
    income: Number(point.income),
    expenses: Number(point.expenses),
  })) ?? [];
  const spending = report?.monthlySpending.map((point) => ({
    name: MONTHS[point.month - 1].slice(0, 3),
    amount: Number(point.amount),
  })) ?? [];
  const categories = report?.expenseByCategory.map((point) => ({ name: point.categoryName, value: Number(point.amount) })) ?? [];
  const budgets = report?.budgetUtilization.map((point) => ({
    name: point.categoryName,
    budget: Number(point.budget),
    spent: Number(point.spent),
  })) ?? [];
  const balances = report?.loanBalanceOverTime.map((point) => ({
    name: MONTHS[point.month - 1].slice(0, 3),
    balance: Number(point.amount),
  })) ?? [];
  const split = report?.interestVsPrincipal.map((point) => ({
    name: MONTHS[point.month - 1].slice(0, 3),
    interest: Number(point.interest),
    principal: Number(point.principal),
  })) ?? [];

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Reports</h1>
          <p className="text-sm text-muted-foreground">Amounts are INR. Transfers are excluded from income and expenses.</p>
        </div>
        <MonthPicker />
      </div>
      <ErrorText message={error} />
      {report && (
        <div className="grid gap-4 lg:grid-cols-2">
          <ChartCard title="Monthly cash flow">
            <ResponsiveContainer width="100%" height={260}>
              <BarChart data={cashFlow}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="name" />
                <YAxis />
                <Tooltip />
                <Legend />
                <Bar dataKey="income" fill="#0f766e" />
                <Bar dataKey="expenses" fill="#e11d48" />
              </BarChart>
            </ResponsiveContainer>
          </ChartCard>
          <ChartCard title="Expense by category">
            {categories.length === 0 ? <p className="text-sm text-muted-foreground">No expenses this month.</p> : (
              <ResponsiveContainer width="100%" height={260}>
                <PieChart>
                  <Pie data={categories} dataKey="value" nameKey="name" innerRadius={55} outerRadius={90}>
                    {categories.map((entry, index) => <Cell key={entry.name} fill={COLORS[index % COLORS.length]} />)}
                  </Pie>
                  <Tooltip />
                  <Legend />
                </PieChart>
              </ResponsiveContainer>
            )}
          </ChartCard>
          <ChartCard title="Monthly spending">
            <ResponsiveContainer width="100%" height={260}>
              <BarChart data={spending}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="name" />
                <YAxis />
                <Tooltip />
                <Bar dataKey="amount" fill="#115e59" />
              </BarChart>
            </ResponsiveContainer>
          </ChartCard>
          <ChartCard title="Budget utilization">
            {budgets.length === 0 ? <p className="text-sm text-muted-foreground">No budgets for this month.</p> : (
              <ResponsiveContainer width="100%" height={260}>
                <BarChart data={budgets}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="name" />
                  <YAxis />
                  <Tooltip />
                  <Legend />
                  <Bar dataKey="budget" fill="#94a3b8" />
                  <Bar dataKey="spent" fill="#0f766e" />
                </BarChart>
              </ResponsiveContainer>
            )}
          </ChartCard>
          <ChartCard title="Loan balance over time">
            <ResponsiveContainer width="100%" height={260}>
              <LineChart data={balances}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="name" />
                <YAxis />
                <Tooltip />
                <Line type="monotone" dataKey="balance" stroke="#0f766e" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </ChartCard>
          <ChartCard title="Interest vs principal">
            <ResponsiveContainer width="100%" height={260}>
              <BarChart data={split}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="name" />
                <YAxis />
                <Tooltip />
                <Legend />
                <Bar dataKey="principal" stackId="loan" fill="#0f766e" />
                <Bar dataKey="interest" stackId="loan" fill="#f59e0b" />
              </BarChart>
            </ResponsiveContainer>
          </ChartCard>
        </div>
      )}
    </div>
  );
}

function ChartCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-lg border bg-white p-4">
      <h2 className="mb-3 font-semibold">{title}</h2>
      {children}
    </section>
  );
}
