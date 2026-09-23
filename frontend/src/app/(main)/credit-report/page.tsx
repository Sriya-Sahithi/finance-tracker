"use client";

import { useEffect, useState } from "react";
import { creditReportApi } from "@/lib/api";
import { formatInr } from "@/lib/format";
import type { CreditReportAccount } from "@/lib/types";
import { CreditReportUploadDialog } from "@/components/credit-report-upload-dialog";
import { Empty, ErrorText } from "@/components/feedback";

export default function CreditReportPage() {
  const [accounts, setAccounts] = useState<CreditReportAccount[]>([]);
  const [error, setError] = useState<string | null>(null);

  function load() {
    creditReportApi.list().then(setAccounts).catch((err) => setError(err.message));
  }

  useEffect(() => { load(); }, []);

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Credit report</h1>
          <p className="text-sm text-muted-foreground">Accounts extracted from your uploaded CIBIL reports.</p>
        </div>
        <CreditReportUploadDialog onSaved={load} />
      </div>
      <ErrorText message={error} />
      {accounts.length === 0 ? (
        <Empty title="No credit report data yet" body="Upload a CIBIL PDF or spreadsheet to see your accounts here." />
      ) : (
        <div className="overflow-x-auto rounded-lg border bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-muted">
              <tr>
                <th className="px-4 py-2 font-medium">Bank</th>
                <th className="px-4 py-2 font-medium">Type</th>
                <th className="px-4 py-2 font-medium">Account no.</th>
                <th className="px-4 py-2 font-medium">Balance</th>
                <th className="px-4 py-2 font-medium">Credit limit</th>
                <th className="px-4 py-2 font-medium">Status</th>
              </tr>
            </thead>
            <tbody>
              {accounts.map((account, index) => (
                <tr key={account.id ?? index} className="border-t">
                  <td className="px-4 py-2">{account.bankName}</td>
                  <td className="px-4 py-2">{account.accountType.replaceAll("_", " ")}</td>
                  <td className="tabular px-4 py-2">{account.accountNumberMasked ?? "—"}</td>
                  <td className="tabular px-4 py-2">{formatInr(account.currentBalance)}</td>
                  <td className="tabular px-4 py-2">{account.creditLimit ? formatInr(account.creditLimit) : "—"}</td>
                  <td className="px-4 py-2">{account.status}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
