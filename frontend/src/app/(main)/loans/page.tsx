"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { loanApi } from "@/lib/api";
import { formatDate, formatInr } from "@/lib/format";
import type { Loan } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Empty, ErrorText } from "@/components/feedback";
import { Field } from "@/components/auth-card";
import { StatementUploadDialog } from "@/components/statement-upload-dialog";

const empty = {
  name: "",
  loanType: "HOME",
  principalAmount: "",
  annualInterestRate: "8.5000",
  tenureMonths: "240",
  startDate: "",
  firstPaymentDate: "",
  paymentDueDay: "5",
  existingLoan: false,
  currentOutstandingPrincipal: "",
  remainingMonths: "",
};

export default function LoansPage() {
  const [loans, setLoans] = useState<Loan[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState(empty);

  function load() {
    loanApi.list().then(setLoans).catch((err) => setError(err.message));
  }

  useEffect(() => { load(); }, []);

  async function create() {
    try {
      await loanApi.create({
        ...form,
        tenureMonths: Number(form.tenureMonths),
        paymentDueDay: Number(form.paymentDueDay),
        currentOutstandingPrincipal: form.existingLoan && form.currentOutstandingPrincipal ? form.currentOutstandingPrincipal : null,
        remainingMonths: form.existingLoan && form.remainingMonths ? Number(form.remainingMonths) : null,
      });
      setOpen(false);
      setForm(empty);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not create loan");
    }
  }

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Loans</h1>
          <p className="text-sm text-muted-foreground">Reducing-balance EMI. Prepayments shorten the tenure and keep the EMI.</p>
        </div>
        <div className="flex gap-2">
          {loans.length > 0 && (
            <StatementUploadDialog
              targetType="LOAN"
              targets={loans.map((loan) => ({ id: loan.id, label: loan.name }))}
              triggerLabel="Upload loan statement"
              onImported={load}
            />
          )}
          <Button type="button" onClick={() => setOpen(true)}>Add loan</Button>
        </div>
      </div>
      <ErrorText message={error} />
      {loans.length === 0 ? <Empty title="No loans" body="Add a home, personal, or other loan to see the EMI and schedule." /> : (
        <div className="grid gap-3">
          {loans.map((loan) => (
            <Link key={loan.id} href={`/loans/${loan.id}`} className="rounded-lg border bg-white p-5 hover:border-teal-700">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="text-sm text-muted-foreground">{loan.loanType}</p>
                  <h2 className="text-lg font-semibold">{loan.name}</h2>
                  <p className="mt-1 text-sm text-muted-foreground">{loan.annualInterestRate}% · {loan.tenureMonths} months · due day {loan.paymentDueDay}</p>
                </div>
                <div className="text-right">
                  <p className="tabular text-xl font-semibold">{formatInr(loan.outstandingPrincipal)}</p>
                  <p className="text-sm text-muted-foreground">EMI {formatInr(loan.emiAmount)}</p>
                </div>
              </div>
              <p className="mt-3 text-sm text-muted-foreground">
                {loan.projectedPayoffDate ? `Projected payoff ${formatDate(loan.projectedPayoffDate)} · ${loan.remainingMonths} EMIs left` : "Paid off"}
              </p>
            </Link>
          ))}
        </div>
      )}
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-h-[90vh] overflow-y-auto">
          <DialogHeader><DialogTitle>Add loan</DialogTitle></DialogHeader>
          <div className="space-y-3">
            <Field label="Name"><Input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} placeholder="Home loan" /></Field>
            <Field label="Type">
              <select className="h-10 w-full rounded-md border bg-white px-3 text-sm" value={form.loanType} onChange={(event) => setForm({ ...form, loanType: event.target.value })}>
                {["HOME", "PERSONAL", "AUTO", "EDUCATION", "OTHER"].map((type) => <option key={type}>{type}</option>)}
              </select>
            </Field>
            <Field label="Principal"><Input value={form.principalAmount} onChange={(event) => setForm({ ...form, principalAmount: event.target.value })} placeholder="2500000.00" /></Field>
            <Field label="Annual interest %"><Input value={form.annualInterestRate} onChange={(event) => setForm({ ...form, annualInterestRate: event.target.value })} /></Field>
            <Field label="Tenure (months)"><Input value={form.tenureMonths} onChange={(event) => setForm({ ...form, tenureMonths: event.target.value })} /></Field>
            <Field label="Start date"><Input type="date" value={form.startDate} onChange={(event) => setForm({ ...form, startDate: event.target.value })} /></Field>
            <Field label="First payment date">
              <Input
                type="date"
                value={form.firstPaymentDate}
                onChange={(event) => {
                  const val = event.target.value;
                  const dayParts = val ? val.split("-") : [];
                  const day = dayParts.length === 3 && dayParts[2] ? String(parseInt(dayParts[2], 10)) : form.paymentDueDay;
                  setForm({ ...form, firstPaymentDate: val, paymentDueDay: day });
                }}
              />
            </Field>
            <Field label="Due day (1–28)"><Input value={form.paymentDueDay} onChange={(event) => setForm({ ...form, paymentDueDay: event.target.value })} /></Field>
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={form.existingLoan} onChange={(event) => setForm({ ...form, existingLoan: event.target.checked })} />
              This is an existing loan with a current outstanding balance
            </label>
            {form.existingLoan && (
              <>
                <Field label="Current outstanding principal"><Input value={form.currentOutstandingPrincipal} onChange={(event) => setForm({ ...form, currentOutstandingPrincipal: event.target.value })} placeholder="800000.00" /></Field>
                <Field label="Remaining months"><Input value={form.remainingMonths} onChange={(event) => setForm({ ...form, remainingMonths: event.target.value })} placeholder="180" /></Field>
              </>
            )}
            <Button type="button" onClick={create}>Calculate EMI and save</Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
