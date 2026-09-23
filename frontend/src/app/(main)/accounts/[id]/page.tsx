"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { accountApi } from "@/lib/api";
import { formatDate, formatInr, maskAccountNumber } from "@/lib/format";
import { formatDate, formatInr } from "@/lib/format";
import { maskAccountNumber } from "@/lib/account-number";
import type { Account, Transaction } from "@/lib/types";
import { ErrorText, Stat } from "@/components/feedback";

export default function AccountDetailPage() {
  const params = useParams<{ id: string }>();
  const [account, setAccount] = useState<Account | null>(null);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    accountApi.get(Number(params.id)).then((detail) => {
      setAccount(detail.account);
      setTransactions(detail.recentTransactions);
    }).catch((err) => setError(err.message));
  }, [params.id]);

  return (
    <div className="space-y-5">
      <ErrorText message={error} />
      {account && (
        <>
          <div>
            <p className="text-sm text-muted-foreground">{account.type.replaceAll("_", " ")} · {account.currency}</p>
            <h1 className="text-2xl font-semibold">{account.name}</h1>
            {account.type === "BANK" && account.accountNumber && (
              <p className="mt-1 text-sm text-muted-foreground">Account {maskAccountNumber(account.accountNumber)}</p>
            )}
            {account.accountNumber && <p className="mt-1 text-sm text-muted-foreground">Account: {maskAccountNumber(account.accountNumber)}</p>}
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <Stat label="Current balance" value={formatInr(account.currentBalance)} />
            <Stat label="Opening balance" value={formatInr(account.openingBalance)} />
          </div>
          <section className="rounded-lg border bg-white">
            <h2 className="border-b px-5 py-4 font-semibold">Recent transactions</h2>
            {transactions.length === 0 ? <p className="px-5 py-8 text-sm text-muted-foreground">No transactions on this account yet.</p> : (
              <ul>
                {transactions.map((transaction) => (
                  <li key={transaction.id} className="flex items-center justify-between border-t px-5 py-3 text-sm first:border-t-0">
                    <div>
                      <p className="font-medium">{transaction.description || transaction.type}</p>
                      <p className="text-muted-foreground">{formatDate(transaction.transactionDate)}</p>
                    </div>
                    <span className="tabular">{formatInr(transaction.amount)}</span>
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

