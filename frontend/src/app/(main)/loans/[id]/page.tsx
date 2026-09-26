"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { accountApi, loanApi } from "@/lib/api";
import { formatDate, formatInr } from "@/lib/format";
import type { Account, Loan, LoanPayment, Prepayment, ScheduleRow } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
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
  const [strategy, setStrategy] = useState<"REDUCE_TENURE" | "REDUCE_EMI">("REDUCE_TENURE");
  const [remainingMonths, setRemainingMonths] = useState("12");
  const [accountId, setAccountId] = useState("");
  const [editOpen, setEditOpen] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);
  const [updating, setUpdating] = useState(false);
  const [editForm, setEditForm] = useState({
    name: "",
    loanType: "HOME" as Loan["loanType"],
    principalAmount: "",
    annualInterestRate: "",
    tenureMonths: "",
    startDate: "",
    firstPaymentDate: "",
    paymentDueDay: "",
    currentOutstandingPrincipal: "",
    remainingMonths: "",
  });

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

  function openEditModal() {
    if (!loan) return;
    setEditError(null);
    setEditForm({
      name: loan.name,
      loanType: loan.loanType,
      principalAmount: loan.principalAmount,
      annualInterestRate: loan.annualInterestRate,
      tenureMonths: String(loan.tenureMonths),
      startDate: loan.startDate,
      firstPaymentDate: loan.firstPaymentDate,
      paymentDueDay: String(loan.paymentDueDay),
      currentOutstandingPrincipal: loan.outstandingPrincipal,
      remainingMonths: String(loan.remainingMonths),
    });
    setEditOpen(true);
  }

  async function updateLoan() {
    setEditError(null);
    setUpdating(true);
    try {
      await loanApi.update(id, {
        name: editForm.name,
        loanType: editForm.loanType,
        principalAmount: editForm.principalAmount,
        annualInterestRate: editForm.annualInterestRate,
        tenureMonths: Number(editForm.tenureMonths),
        startDate: editForm.startDate,
        firstPaymentDate: editForm.firstPaymentDate,
        paymentDueDay: Number(editForm.paymentDueDay),
        currentOutstandingPrincipal: editForm.currentOutstandingPrincipal ? editForm.currentOutstandingPrincipal : null,
        remainingMonths: editForm.remainingMonths ? Number(editForm.remainingMonths) : null,
      });
      setEditOpen(false);
      load();
    } catch (err) {
      setEditError(err instanceof Error ? err.message : "Could not update loan");
    } finally {
      setUpdating(false);
    }
  }

  useEffect(() => { load(); }, [id]);

  async function pay(prepay: boolean) {
    setError(null);
    setResult(null);
    const body = {
      paymentDate,
      extraPrincipalAmount: extra || "0",
      strategy,
      remainingMonths: strategy === "REDUCE_EMI" ? Number(remainingMonths || 0) : null,
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
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-sm text-muted-foreground">{loan.loanType} · {loan.annualInterestRate}% annual</p>
              <h1 className="text-2xl font-semibold">{loan.name}</h1>
            </div>
            <Button type="button" variant="outline" onClick={openEditModal}>Edit loan</Button>
          </div>
          <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Stat label="Outstanding" value={formatInr(loan.outstandingPrincipal)} />
            <Stat label="EMI" value={formatInr(loan.emiAmount)} />
            <Stat label="Remaining EMIs" value={String(loan.remainingMonths)} />
            <Stat label="Payoff" value={formatDate(loan.projectedPayoffDate)} />
          </section>
          <section className="rounded-lg border bg-white p-5">
            <h2 className="font-semibold">Record a payment</h2>
            <p className="mb-4 mt-1 text-sm text-muted-foreground">Choose whether extra principal reduces your tenure, or reduces the EMI while keeping the remaining months fixed.</p>
            <div className="grid gap-3 md:grid-cols-5">
              <Field label="Date"><Input type="date" value={paymentDate} onChange={(event) => setPaymentDate(event.target.value)} /></Field>
              <Field label="Extra principal"><Input value={extra} onChange={(event) => setExtra(event.target.value)} /></Field>
              <Field label="Strategy">
                <select className="h-10 w-full rounded-md border bg-white px-3 text-sm" value={strategy} onChange={(event) => setStrategy(event.target.value as "REDUCE_TENURE" | "REDUCE_EMI")}>
                  <option value="REDUCE_TENURE">Reduce tenure</option>
                  <option value="REDUCE_EMI">Reduce EMI</option>
                </select>
              </Field>
              {strategy === "REDUCE_EMI" ? (
                <Field label="Remaining months"><Input value={remainingMonths} onChange={(event) => setRemainingMonths(event.target.value)} /></Field>
              ) : (
                <Field label="Remaining months"><Input value={String(loan.remainingMonths)} disabled /></Field>
              )}
              <Field label="Pay from account">
                <select className="h-10 w-full rounded-md border bg-white px-3 text-sm" value={accountId} onChange={(event) => setAccountId(event.target.value)}>
                  <option value="">Do not post to an account</option>
                  {accounts.map((account) => <option key={account.id} value={account.id}>{account.name}</option>)}
                </select>
              </Field>
            </div>
            <div className="mt-4 flex items-end gap-2">
              <Button type="button" variant="outline" onClick={() => pay(false)}>Pay EMI</Button>
              <Button type="button" onClick={() => pay(true)}>Prepay</Button>
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
          <Dialog open={editOpen} onOpenChange={setEditOpen}>
            <DialogContent className="max-h-[90vh] overflow-y-auto">
              <DialogHeader><DialogTitle>Edit loan & recalibrate</DialogTitle></DialogHeader>
              {editError && <ErrorText message={editError} />}
              <div className="space-y-3">
                <Field label="Name"><Input value={editForm.name} onChange={(event) => setEditForm({ ...editForm, name: event.target.value })} placeholder="Home loan" /></Field>
                <Field label="Type">
                  <select className="h-10 w-full rounded-md border bg-white px-3 text-sm" value={editForm.loanType} onChange={(event) => setEditForm({ ...editForm, loanType: event.target.value as Loan["loanType"] })}>
                    {["HOME", "PERSONAL", "AUTO", "EDUCATION", "OTHER"].map((type) => <option key={type}>{type}</option>)}
                  </select>
                </Field>
                <Field label="Original Principal"><Input value={editForm.principalAmount} onChange={(event) => setEditForm({ ...editForm, principalAmount: event.target.value })} placeholder="2500000.00" /></Field>
                <Field label="Current outstanding principal"><Input value={editForm.currentOutstandingPrincipal} onChange={(event) => setEditForm({ ...editForm, currentOutstandingPrincipal: event.target.value })} placeholder="800000.00" /></Field>
                <Field label="Annual interest %"><Input value={editForm.annualInterestRate} onChange={(event) => setEditForm({ ...editForm, annualInterestRate: event.target.value })} /></Field>
                <Field label="Remaining EMIs (months)"><Input value={editForm.remainingMonths} onChange={(event) => setEditForm({ ...editForm, remainingMonths: event.target.value })} placeholder="180" /></Field>
                <Field label="Total tenure (months)"><Input value={editForm.tenureMonths} onChange={(event) => setEditForm({ ...editForm, tenureMonths: event.target.value })} /></Field>
                <Field label="Start date"><Input type="date" value={editForm.startDate} onChange={(event) => setEditForm({ ...editForm, startDate: event.target.value })} /></Field>
                <Field label="First payment date">
                  <Input
                    type="date"
                    value={editForm.firstPaymentDate}
                    onChange={(event) => {
                      const val = event.target.value;
                      const dayParts = val ? val.split("-") : [];
                      const day = dayParts.length === 3 && dayParts[2] ? String(parseInt(dayParts[2], 10)) : editForm.paymentDueDay;
                      setEditForm({ ...editForm, firstPaymentDate: val, paymentDueDay: day });
                    }}
                  />
                </Field>
                <Field label="Due day (1–28)"><Input value={editForm.paymentDueDay} onChange={(event) => setEditForm({ ...editForm, paymentDueDay: event.target.value })} /></Field>
                {editError && <ErrorText message={editError} />}
                <Button type="button" onClick={updateLoan} disabled={updating}>
                  {updating ? "Saving..." : "Save changes & recalculate"}
                </Button>
              </div>
            </DialogContent>
          </Dialog>
        </>
      )}
    </div>
  );
}
