"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { accountApi, statementImportApi } from "@/lib/api";
import { formatDate, formatInr } from "@/lib/format";
import { maskAccountNumber } from "@/lib/account-number";
import type { Account, StatementImportConfirmResponse, StatementImportPreview } from "@/lib/types";
import { Field } from "@/components/auth-card";
import { Empty, ErrorText } from "@/components/feedback";
import { Button } from "@/components/ui/button";

export default function StatementImportPage() {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<StatementImportPreview | null>(null);
  const [selectedAccountId, setSelectedAccountId] = useState("");
  const [selectedFingerprints, setSelectedFingerprints] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [confirming, setConfirming] = useState(false);

  useEffect(() => {
    accountApi.list().then(setAccounts).catch((err) => setError(err.message));
  }, []);

  useEffect(() => {
    if (!preview) return;
    setSelectedAccountId(preview.detectedAccount.suggestedAccountId ? String(preview.detectedAccount.suggestedAccountId) : "");
    setSelectedFingerprints(preview.rows.map((row) => row.fingerprint));
  }, [preview]);

  const selectedSummary = useMemo(() => {
    if (!preview) return { income: "0.00", expense: "0.00" };
    let income = 0;
    let expense = 0;
    const selected = new Set(selectedFingerprints);
    for (const row of preview.rows) {
      if (!selected.has(row.fingerprint)) continue;
      if (row.type === "INCOME") income += Number(row.amount);
      else expense += Number(row.amount);
    }
    return { income: income.toFixed(2), expense: expense.toFixed(2) };
  }, [preview, selectedFingerprints]);

  async function handlePreview() {
    if (!file) {
      setError("Choose a CSV file to preview");
      return;
    }
    setUploading(true);
    setError(null);
    setSuccess(null);
    try {
      const nextPreview = await statementImportApi.preview(file);
      setPreview(nextPreview);
    } catch (err) {
      setPreview(null);
      setSelectedFingerprints([]);
      setSelectedAccountId("");
      setError(err instanceof Error ? err.message : "Could not preview CSV");
    } finally {
      setUploading(false);
    }
  }

  async function handleConfirm() {
    if (!preview) return;
    if (!selectedAccountId) {
      setError("Select the account that should receive the imported transactions");
      return;
    }
    if (selectedFingerprints.length === 0) {
      setError("Select at least one valid row to import");
      return;
    }
    setConfirming(true);
    setError(null);
    setSuccess(null);
    try {
      const result = await statementImportApi.confirm(preview.sessionId, {
        accountId: Number(selectedAccountId),
        rowFingerprints: selectedFingerprints,
      });
      setSuccess(buildSuccessMessage(result));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not confirm import");
    } finally {
      setConfirming(false);
    }
  }

  function toggleFingerprint(fingerprint: string) {
    setSelectedFingerprints((current) => current.includes(fingerprint)
      ? current.filter((value) => value !== fingerprint)
      : [...current, fingerprint]);
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Import bank statement CSV</h1>
          <p className="text-sm text-muted-foreground">Preview CSV bank statement rows before creating income and expense transactions.</p>
        </div>
        <Button asChild variant="outline"><Link href="/transactions">Back to transactions</Link></Button>
      </div>

      <div className="rounded-lg border bg-white p-5">
        <div className="space-y-3">
          <Field label="CSV file">
            <input
              className="block w-full text-sm"
              type="file"
              accept=".csv,text/csv"
              onChange={(event) => setFile(event.target.files?.[0] || null)}
            />
          </Field>
          <p className="text-xs text-muted-foreground">Supported headers include date, narration/description, debit, credit, amount, transaction type, reference, account name, and account number.</p>
          <div className="flex gap-2">
            <Button type="button" onClick={handlePreview} disabled={uploading}>{uploading ? "Previewing…" : "Preview import"}</Button>
            {preview && <Button type="button" variant="outline" onClick={() => { setPreview(null); setSelectedFingerprints([]); setSuccess(null); }}>Clear preview</Button>}
          </div>
        </div>
      </div>

      <ErrorText message={error} />
      {success && <p className="rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-700">{success}</p>}

      {preview ? (
        <div className="space-y-5">
          <div className="grid gap-3 md:grid-cols-3">
            <div className="rounded-lg border bg-white p-4">
              <p className="text-sm text-muted-foreground">Rows ready to import</p>
              <p className="mt-1 text-2xl font-semibold">{preview.summary.validRows}</p>
              <p className="mt-1 text-xs text-muted-foreground">Skipped rows: {preview.summary.skippedRows}</p>
            </div>
            <div className="rounded-lg border bg-white p-4">
              <p className="text-sm text-muted-foreground">Income selected</p>
              <p className="mt-1 text-2xl font-semibold text-emerald-700">{formatInr(selectedSummary.income)}</p>
            </div>
            <div className="rounded-lg border bg-white p-4">
              <p className="text-sm text-muted-foreground">Expense selected</p>
              <p className="mt-1 text-2xl font-semibold text-rose-700">{formatInr(selectedSummary.expense)}</p>
            </div>
          </div>

          <div className="rounded-lg border bg-white p-5">
            <div className="space-y-3">
              <div>
                <h2 className="font-semibold">Detected account</h2>
                <p className="text-sm text-muted-foreground">
                  {preview.detectedAccount.accountName || "No account name found"}
                  {preview.detectedAccount.accountNumber ? ` · ${maskAccountNumber(preview.detectedAccount.accountNumber)}` : ""}
                </p>
                {preview.detectedAccount.suggestedAccountName && (
                  <p className="text-xs text-muted-foreground">Suggested match: {preview.detectedAccount.suggestedAccountName} ({preview.detectedAccount.matchedBy === "ACCOUNT_NUMBER" ? "matched by account number" : "matched by account name"})</p>
                )}
              </div>
              <Field label="Import into account">
                <select className="h-10 w-full rounded-md border bg-white px-3 text-sm" value={selectedAccountId} onChange={(event) => setSelectedAccountId(event.target.value)}>
                  <option value="">Select an existing account</option>
                  {accounts.map((account) => (
                    <option key={account.id} value={account.id}>{account.name}{account.accountNumber ? ` (${maskAccountNumber(account.accountNumber)})` : ""}</option>
                  ))}
                </select>
              </Field>
            </div>
          </div>

          {preview.warnings.length > 0 && (
            <div className="rounded-lg border bg-amber-50 p-4 text-sm text-amber-900">
              <p className="font-medium">Warnings</p>
              <ul className="mt-2 list-disc pl-5">
                {preview.warnings.map((warning) => <li key={warning}>{warning}</li>)}
              </ul>
            </div>
          )}

          {preview.skippedRows.length > 0 && (
            <div className="rounded-lg border bg-white p-5">
              <h2 className="font-semibold">Skipped rows</h2>
              <ul className="mt-3 space-y-2 text-sm text-muted-foreground">
                {preview.skippedRows.map((row) => <li key={`${row.rowNumber}-${row.reason}`}>Row {row.rowNumber}: {row.reason}</li>)}
              </ul>
            </div>
          )}

          {preview.rows.length === 0 ? <Empty title="No valid rows found" body="Fix the CSV warnings or choose another file to continue." /> : (
            <div className="rounded-lg border bg-white">
              <div className="flex flex-wrap items-center justify-between gap-3 border-b px-5 py-4">
                <div>
                  <h2 className="font-semibold">Preview rows</h2>
                  <p className="text-sm text-muted-foreground">Select the rows to import.</p>
                </div>
                <div className="flex gap-2">
                  <Button type="button" size="sm" variant="outline" onClick={() => setSelectedFingerprints(preview.rows.map((row) => row.fingerprint))}>Select all</Button>
                  <Button type="button" size="sm" variant="outline" onClick={() => setSelectedFingerprints([])}>Clear</Button>
                </div>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="border-b text-left text-muted-foreground">
                    <tr>
                      <th className="px-4 py-3 font-medium">Include</th>
                      <th className="px-4 py-3 font-medium">Date</th>
                      <th className="px-4 py-3 font-medium">Description</th>
                      <th className="px-4 py-3 font-medium">Reference</th>
                      <th className="px-4 py-3 font-medium">Type</th>
                      <th className="px-4 py-3 text-right font-medium">Amount</th>
                    </tr>
                  </thead>
                  <tbody>
                    {preview.rows.map((row) => {
                      const checked = selectedFingerprints.includes(row.fingerprint);
                      return (
                        <tr key={row.fingerprint} className="border-t">
                          <td className="px-4 py-3"><input type="checkbox" checked={checked} onChange={() => toggleFingerprint(row.fingerprint)} aria-label={`Include ${row.type === "INCOME" ? "income" : "expense"} row ${row.rowNumber}: ${row.description} on ${formatDate(row.transactionDate)} for ${formatInr(row.amount)}`} /></td>
                          <td className="px-4 py-3">{formatDate(row.transactionDate)}</td>
                          <td className="px-4 py-3">
                            <p className="font-medium">{row.description}</p>
                            <p className="text-xs text-muted-foreground">Row {row.rowNumber}</p>
                          </td>
                          <td className="px-4 py-3">{row.reference || "—"}</td>
                          <td className="px-4 py-3">{row.type === "INCOME" ? "Income" : "Expense"}</td>
                          <td className={`px-4 py-3 text-right font-medium ${row.type === "INCOME" ? "text-emerald-700" : "text-rose-700"}`}>{formatInr(row.amount)}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm text-muted-foreground">{selectedFingerprints.length} row(s) selected for confirmation.</p>
            <Button type="button" onClick={handleConfirm} disabled={confirming || preview.rows.length === 0}>{confirming ? "Importing…" : "Confirm import"}</Button>
          </div>
        </div>
      ) : (
        <Empty title="No CSV preview yet" body="Upload a CSV bank statement to review and import its rows." />
      )}
    </div>
  );
}


function buildSuccessMessage(result: StatementImportConfirmResponse) {
  if (result.importedCount === 0) {
    return `No new rows were imported. ${result.duplicateCount} selected row(s) were already imported earlier.`;
  }
  return `Imported ${result.importedCount} row(s). ${result.duplicateCount} duplicate row(s) were skipped. Updated account balance: ${formatInr(result.accountBalance)}.`;
}
