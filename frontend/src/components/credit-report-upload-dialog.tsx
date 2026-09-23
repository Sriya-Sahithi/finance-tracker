"use client";

import { useState } from "react";
import { creditReportApi } from "@/lib/api";
import type { CreditReportAccount, CreditReportParsePreview } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { ErrorText } from "@/components/feedback";
import { Field } from "@/components/auth-card";

export function CreditReportUploadDialog({ onSaved }: { onSaved: () => void }) {
  const [open, setOpen] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<CreditReportParsePreview | null>(null);
  const [accounts, setAccounts] = useState<CreditReportAccount[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  function reset() {
    setFile(null);
    setPreview(null);
    setAccounts([]);
    setError(null);
    setSuccess(null);
  }

  async function loadPreview() {
    if (!file) {
      setError("Choose a .pdf or .xlsx credit report first");
      return;
    }
    setError(null);
    setLoading(true);
    try {
      const result = await creditReportApi.preview(file);
      setPreview(result);
      setAccounts(result.accounts);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not parse credit report");
    } finally {
      setLoading(false);
    }
  }

  function updateAccount(index: number, patch: Partial<CreditReportAccount>) {
    setAccounts((current) => current.map((account, i) => (i === index ? { ...account, ...patch } : account)));
  }

  function addAccount() {
    setAccounts((current) => [
      ...current,
      { id: null, bankName: "", accountType: "BANK", accountNumberMasked: null, currentBalance: "0.00", creditLimit: null, status: "ACTIVE", reportDate: null },
    ]);
  }

  function removeAccount(index: number) {
    setAccounts((current) => current.filter((_, i) => i !== index));
  }

  async function confirmSave() {
    if (accounts.length === 0) {
      setError("Add at least one account before saving");
      return;
    }
    setError(null);
    setLoading(true);
    try {
      await creditReportApi.commit({ accounts, sourceFileName: preview?.sourceFileName ?? null });
      setSuccess(`Saved ${accounts.length} credit report account(s).`);
      onSaved();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save credit report accounts");
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <Button type="button" onClick={() => setOpen(true)}>Upload CIBIL report</Button>
      <Dialog
        open={open}
        onOpenChange={(next) => {
          setOpen(next);
          if (!next) reset();
        }}
      >
        <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-3xl">
          <DialogHeader>
            <DialogTitle>Upload CIBIL report</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <ErrorText message={error} />
            {success && <p className="rounded-md bg-teal-50 px-3 py-2 text-sm text-teal-800">{success}</p>}

            {!preview && (
              <div className="space-y-3">
                <Field label="Credit report file (.pdf or .xlsx)">
                  <Input type="file" accept=".pdf,.xlsx" onChange={(event) => setFile(event.target.files?.[0] ?? null)} />
                </Field>
                <Button type="button" onClick={loadPreview} disabled={loading}>
                  {loading ? "Parsing…" : "Preview"}
                </Button>
              </div>
            )}

            {preview && !success && (
              <div className="space-y-3">
                {preview.warning && (
                  <p className="rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800">{preview.warning}</p>
                )}
                <div className="space-y-3">
                  {accounts.map((account, index) => (
                    <div key={index} className="grid gap-2 rounded-md border p-3 sm:grid-cols-6">
                      <Input
                        className="sm:col-span-2"
                        placeholder="Bank / lender name"
                        value={account.bankName}
                        onChange={(event) => updateAccount(index, { bankName: event.target.value })}
                      />
                      <select
                        className="h-10 rounded-md border bg-white px-2 text-sm"
                        value={account.accountType}
                        onChange={(event) => updateAccount(index, { accountType: event.target.value as CreditReportAccount["accountType"] })}
                      >
                        <option value="BANK">Bank</option>
                        <option value="CREDIT_CARD">Credit card</option>
                        <option value="LOAN">Loan</option>
                        <option value="OTHER">Other</option>
                      </select>
                      <Input
                        placeholder="Balance"
                        value={account.currentBalance}
                        onChange={(event) => updateAccount(index, { currentBalance: event.target.value })}
                      />
                      <Input
                        placeholder="Credit limit"
                        value={account.creditLimit ?? ""}
                        onChange={(event) => updateAccount(index, { creditLimit: event.target.value || null })}
                      />
                      <select
                        className="h-10 rounded-md border bg-white px-2 text-sm"
                        value={account.status}
                        onChange={(event) => updateAccount(index, { status: event.target.value as CreditReportAccount["status"] })}
                      >
                        <option value="ACTIVE">Active</option>
                        <option value="CLOSED">Closed</option>
                      </select>
                      <Button type="button" variant="ghost" size="sm" onClick={() => removeAccount(index)}>
                        Remove
                      </Button>
                    </div>
                  ))}
                </div>
                <Button type="button" variant="outline" size="sm" onClick={addAccount}>
                  Add account row
                </Button>
                <div className="flex gap-2">
                  <Button type="button" onClick={confirmSave} disabled={loading}>
                    {loading ? "Saving…" : "Confirm & save"}
                  </Button>
                  <Button type="button" variant="ghost" onClick={reset}>
                    Start over
                  </Button>
                </div>
              </div>
            )}
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}
