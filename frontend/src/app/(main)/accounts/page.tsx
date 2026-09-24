"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { accountApi } from "@/lib/api";
import { formatInr, maskAccountNumber } from "@/lib/format";
import type { Account } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Empty, ErrorText } from "@/components/feedback";
import { Field } from "@/components/auth-card";
import { StatementUploadDialog } from "@/components/statement-upload-dialog";

export default function AccountsPage() {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [type, setType] = useState("BANK");
  const [accountNumber, setAccountNumber] = useState("");
  const [openingBalance, setOpeningBalance] = useState("0.00");
  const [deletingId, setDeletingId] = useState<number | null>(null);

  function load() {
    accountApi.list().then(setAccounts).catch((err) => setError(err.message));
  }

  useEffect(() => { load(); }, []);

  async function create() {
    try {
      await accountApi.create({
        name,
        type,
        accountNumber: type === "BANK" && accountNumber.trim() ? accountNumber : undefined,
        openingBalance,
        currency: "INR",
      });
      setOpen(false);
      setName("");
      setAccountNumber("");
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not create account");
    }
  }

  async function removeAccount(account: Account) {
    if (!window.confirm(`Delete ${account.name}? Accounts with transactions cannot be deleted.`)) return;

    setError(null);
    setDeletingId(account.id);
    try {
      await accountApi.remove(account.id);
      setAccounts((current) => current.filter((item) => item.id !== account.id));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not delete account");
    } finally {
      setDeletingId(null);
    }
  }

  return (
      <div className="space-y-5">
        <div className="flex items-center justify-between gap-3">
          <div>
            <h1 className="text-2xl font-semibold">Accounts</h1>
            <p className="text-sm text-muted-foreground">Bank, cash, and card balances in INR.</p>
          </div>
          <div className="flex gap-2">
            {accounts.length > 0 && (
                <StatementUploadDialog
                    targetType="ACCOUNT"
                    targets={accounts.map((account) => ({ id: account.id, label: account.name }))}
                    triggerLabel="Upload bank statement"
                    onImported={load}
                />
            )}
            <Button type="button" onClick={() => setOpen(true)}>Add account</Button>
          </div>
        </div>
        <ErrorText message={error} />
        {accounts.length === 0 ? <Empty title="No accounts yet" body="Add a bank account or a cash wallet to start recording transactions." /> : (
            <div className="grid gap-3 sm:grid-cols-2">
              {accounts.map((account) => (
                  <div key={account.id} className="rounded-lg border bg-white p-5 hover:border-teal-700">
                    <Link href={`/accounts/${account.id}`} className="block">
                      <p className="text-sm text-muted-foreground">{account.type.replaceAll("_", " ")}</p>
                      <h2 className="mt-1 text-lg font-semibold">{account.name}</h2>
                      {account.type === "BANK" && account.accountNumber && (
                          <p className="mt-1 text-sm text-muted-foreground">A/c {maskAccountNumber(account.accountNumber)}</p>
                      )}
                      {account.accountNumber && <p className="mt-1 text-sm text-muted-foreground">{maskAccountNumber(account.accountNumber)}</p>}
                      <p className="tabular mt-3 text-2xl font-semibold">{formatInr(account.currentBalance)}</p>
                    </Link>
                    <div className="mt-4 flex justify-end">
                      <Button
                          type="button"
                          variant="destructive"
                          size="sm"
                          onClick={() => removeAccount(account)}
                          disabled={deletingId === account.id}
                      >
                        {deletingId === account.id ? "Deleting…" : "Delete"}
                      </Button>
                    </div>
                  </div>
              ))}
            </div>
        )}
        <Dialog open={open} onOpenChange={setOpen}>
          <DialogContent>
            <DialogHeader><DialogTitle>Add account</DialogTitle></DialogHeader>
            <div className="space-y-3">
              <Field label="Name"><Input value={name} onChange={(event) => setName(event.target.value)} placeholder="HDFC Savings" /></Field>
              <Field label="Type">
                <select
                    className="h-10 w-full rounded-md border bg-white px-3 text-sm"
                    value={type}
                    onChange={(event) => {
                      setType(event.target.value);
                      if (event.target.value !== "BANK") setAccountNumber("");
                    }}
                >
                  <option value="BANK">Bank</option>
                  <option value="CASH">Cash</option>
                  <option value="CREDIT_CARD">Credit card</option>
                  <option value="OTHER">Other</option>
                </select>
              </Field>
              {type === "BANK" && (
                  <Field label="Account number">
                    <Input
                        value={accountNumber}
                        onChange={(event) => setAccountNumber(event.target.value)}
                        placeholder="1234 5678 9012"
                    />
                  </Field>
              )}
              <Field label="Opening balance"><Input value={openingBalance} onChange={(event) => setOpeningBalance(event.target.value)} /></Field>
              <Button type="button" onClick={create}>Save</Button>
            </div>
          </DialogContent>
        </Dialog>
      </div>
  );
}