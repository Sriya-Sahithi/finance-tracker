"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { accountApi, loanApi } from "@/lib/api";
import { formatDate, formatInr } from "@/lib/format";
import type { Account, Loan, LoanPayment, Prepayment, ScheduleRow } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ErrorText, Stat } from "@/components/feedback";
import { Field } from "@/components/auth-card";

export default function LoanDetailPage() {
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  const [loan, setLoan] = useState<Loan | null>(null);
  const [schedule, setSchedule] = useState<ScheduleRow[]>([]);
  const [payments, setPayments] = useState<LoanPayment[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<Prepayment | null>(null);
  const [paymentDate, setPaymentDate] = useState(new Date().toISOString().slice(0, 10));
  const [extra, setExtra] = useState("0.00");
  const [accountId, setAccountId] = useState("");

  function load() {
    Promise.all([loanApi.get(id), loanApi.schedule(id), loanApi.payments(id), accountApi.list()])
      .then(([nextLoan, nextSchedule, nextPayments, nextAccounts]) => {
        setLoan(nextLoan);
        setSchedule(nextSchedule.schedule);
        setPayments(nextPayments);
        setAccounts(nextAccounts);
      })
      .catch((err) => setError(err.message));
  }

  useEffect(() => { load(); }, [id]);

  async function pay(prepay: boolean) {
    setError(null);
    setResult(null);
    const body = {
      paymentDate,
      extraPrincipalAmount: extra || "0",
      accountId: accountId ? Number(accountId) : null,
    };
    try {
      if (prepay) setResult(await loanApi.prepay(id, body));
      else await loanApi.pay(id, body);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Payment failed");
    }
  }

  return (
    <div className="space-y-6">
      <ErrorText message={error} />
      {loan && (
        <>
          <div>
            <p className="text-sm text-muted-foreground">{loan.loanType} · {loan.annualInterestRate}% annual</p>
            <h1 className="text-2xl font-semibold">{loan.name}</h1>
          </div>
          <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Stat label="Outstanding" value={formatInr(loan.outstandingPrincipal)} />
            <Stat label="EMI" value={formatInr(loan.emiAmount)} />
            <Stat label="Remaining EMIs" value={String(loan.remainingMonths)} />
            <Stat label="Payoff" value={formatDate(loan.projectedPayoffDate)} />
          </section>
          <section className="rounded-lg border bg-white p-5">
            <h2 className="font-semibold">Record a payment</h2>
            <p className="mb-4 mt-1 text-sm text-muted-foreground">Extra principal is applied on top of the EMI and reduces the tenure.</p>
            <div className="grid gap-3 md:grid-cols-4">
              <Field label="Date"><Input type="date" value={paymentDate} onChange={(event) => setPaymentDate(event.target.value)} /></Field>
              <Field label="Extra principal"><Input value={extra} onChange={(event) => setExtra(event.target.value)} /></Field>
              <Field label="Pay from account">
                <select className="h-10 w-full rounded-md border bg-white px-3 text-sm" value={accountId} onChange={(event) => setAccountId(event.target.value)}>
                  <option value="">Do not post to an account</option>
                  {accounts.map((account) => <option key={account.id} value={account.id}>{account.name}</option>)}
                </select>
              </Field>
              <div className="flex items-end gap-2">
                <Button type="button" variant="outline" onClick={() => pay(false)}>Pay EMI</Button>
                <Button type="button" onClick={() => pay(true)}>Prepay</Button>
              </div>
            </div>
            {result && (
              <dl className="mt-4 grid gap-2 rounded-md bg-teal-50 p-4 text-sm sm:grid-cols-2">
                <div><dt className="text-muted-foreground">Interest saved</dt><dd className="tabular font-medium">{formatInr(result.interestSaved)}</dd></div>
                <div><dt className="text-muted-foreground">New outstanding</dt><dd className="tabular font-medium">{formatInr(result.newOutstanding)}</dd></div>
                <div><dt className="text-muted-foreground">EMIs reduced</dt><dd className="font-medium">{result.emisReduced}</dd></div>
                <div><dt className="text-muted-foreground">New payoff</dt><dd className="font-medium">{formatDate(result.newPayoffDate)}</dd></div>
              </dl>
            )}
          </section>
          <section className="overflow-x-auto rounded-lg border bg-white">
            <h2 className="border-b px-5 py-4 font-semibold">Amortization schedule</h2>
            <table className="w-full text-sm">
              <thead className="text-left text-muted-foreground">
                <tr>
                  {["#", "Date", "Opening", "EMI", "Interest", "Principal", "Extra", "Closing", ""].map((heading) => (
                    <th key={heading} className="px-3 py-2 font-medium">{heading}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {schedule.map((row) => (
                  <tr key={`${row.kind}-${row.paymentNumber}`} className="border-t">
                    <td className="px-3 py-2">{row.paymentNumber}</td>
                    <td className="px-3 py-2">{formatDate(row.date)}</td>
                    <td className="tabular px-3 py-2">{formatInr(row.openingPrincipal)}</td>
                    <td className="tabular px-3 py-2">{formatInr(row.emi)}</td>
                    <td className="tabular px-3 py-2">{formatInr(row.interest)}</td>
                    <td className="tabular px-3 py-2">{formatInr(row.principal)}</td>
                    <td className="tabular px-3 py-2">{formatInr(row.extraPrincipal)}</td>
                    <td className="tabular px-3 py-2">{formatInr(row.closingPrincipal)}</td>
                    <td className="px-3 py-2 text-xs text-muted-foreground">{row.kind}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
          <section className="rounded-lg border bg-white">
            <h2 className="border-b px-5 py-4 font-semibold">Payment history</h2>
            {payments.length === 0 ? <p className="px-5 py-6 text-sm text-muted-foreground">No payments recorded yet.</p> : (
              <ul>
                {payments.map((payment) => (
                  <li key={payment.id} className="flex justify-between gap-3 border-t px-5 py-3 text-sm first:border-t-0">
                    <span>{formatDate(payment.paymentDate)}</span>
                    <span className="tabular">{formatInr(payment.totalAmount)} · left {formatInr(payment.remainingPrincipal)}</span>
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
