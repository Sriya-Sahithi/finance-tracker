"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { accountApi, categoryApi, transactionApi } from "@/lib/api";
import { formatDate, formatInr } from "@/lib/format";
import type { Account, Category, Transaction } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Empty, ErrorText } from "@/components/feedback";
import { Field } from "@/components/auth-card";

const schema = z.object({
  type: z.enum(["INCOME", "EXPENSE", "TRANSFER", "LOAN_PAYMENT"]),
  amount: z.string().regex(/^\d+(\.\d{1,2})?$/, "Enter an amount greater than 0").refine((value) => Number(value) > 0, "Enter an amount greater than 0"),
  transactionDate: z.string().min(1, "Date is required"),
  accountId: z.string().min(1, "Account is required"),
  transferAccountId: z.string().optional(),
  categoryId: z.string().optional(),
  description: z.string().max(255).optional(),
  notes: z.string().max(1000).optional(),
});

type FormValues = z.infer<typeof schema>;

export default function TransactionsPage() {
  const [rows, setRows] = useState<Transaction[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [search, setSearch] = useState("");
  const [type, setType] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<Transaction | null>(null);

  async function load(nextPage = page) {
    const query = new URLSearchParams({ page: String(nextPage), size: "10" });
    if (search.trim()) query.set("search", search.trim());
    if (type) query.set("type", type);
    const result = await transactionApi.list(`?${query.toString()}`);
    setRows(result.content);
    setTotalPages(result.totalPages);
    setPage(result.page);
  }

  useEffect(() => {
    Promise.all([accountApi.list(), categoryApi.list()]).then(([nextAccounts, nextCategories]) => {
      setAccounts(nextAccounts);
      setCategories(nextCategories);
    }).catch((err) => setError(err.message));
  }, []);

  useEffect(() => {
    load(0).catch((err) => setError(err.message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [type]);

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Transactions</h1>
          <p className="text-sm text-muted-foreground">Transfers move money between accounts and are excluded from income and expenses.</p>
        </div>
        <div className="flex gap-2">
          <Button asChild type="button" variant="outline"><Link href="/transactions/import">Import CSV</Link></Button>
          <Button type="button" onClick={() => { setEditing(null); setOpen(true); }}>Add transaction</Button>
        </div>
      </div>
      <ErrorText message={error} />
      <div className="flex flex-wrap gap-2">
        <Input className="max-w-xs" placeholder="Search description or notes" value={search} onChange={(event) => setSearch(event.target.value)} />
        <select className="h-10 rounded-md border bg-white px-3 text-sm" value={type} onChange={(event) => setType(event.target.value)} aria-label="Type">
          <option value="">All types</option>
          <option value="INCOME">Income</option>
          <option value="EXPENSE">Expense</option>
          <option value="TRANSFER">Transfer</option>
          <option value="LOAN_PAYMENT">Loan payment</option>
        </select>
        <Button type="button" variant="outline" onClick={() => load(0).catch((err) => setError(err.message))}>Search</Button>
      </div>
      {rows.length === 0 ? <Empty title="No transactions" body="Add income, an expense, or a transfer between your accounts." /> : (
        <div className="overflow-x-auto rounded-lg border bg-white">
          <table className="w-full text-sm">
            <thead className="border-b text-left text-muted-foreground">
              <tr>
                <th className="px-4 py-3 font-medium">Date</th>
                <th className="px-4 py-3 font-medium">Description</th>
                <th className="px-4 py-3 font-medium">Category</th>
                <th className="px-4 py-3 font-medium">Account</th>
                <th className="px-4 py-3 text-right font-medium">Amount</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id} className="border-t">
                  <td className="px-4 py-3">{formatDate(row.transactionDate)}</td>
                  <td className="px-4 py-3">
                    <p className="font-medium">{row.description || row.type.replaceAll("_", " ")}</p>
                    <p className="text-xs text-muted-foreground">{row.type.replaceAll("_", " ")}</p>
                  </td>
                  <td className="px-4 py-3">{row.categoryName || "—"}</td>
                  <td className="px-4 py-3">{row.transferAccountName ? `${row.accountName} → ${row.transferAccountName}` : row.accountName}</td>
                  <td className={`tabular px-4 py-3 text-right font-medium ${row.type === "INCOME" ? "text-emerald-700" : row.type === "TRANSFER" ? "" : "text-rose-700"}`}>{formatInr(row.amount)}</td>
                  <td className="px-4 py-3 text-right">
                    {!row.loanId && (
                      <div className="flex justify-end gap-2">
                        <Button type="button" size="sm" variant="outline" onClick={() => { setEditing(row); setOpen(true); }}>Edit</Button>
                        <Button type="button" size="sm" variant="ghost" onClick={() => transactionApi.remove(row.id).then(() => load()).catch((err) => setError(err.message))}>Delete</Button>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <div className="flex items-center justify-between text-sm">
        <span className="text-muted-foreground">Page {totalPages === 0 ? 0 : page + 1} of {totalPages}</span>
        <div className="flex gap-2">
          <Button type="button" variant="outline" size="sm" disabled={page <= 0} onClick={() => load(page - 1)}>Previous</Button>
          <Button type="button" variant="outline" size="sm" disabled={page + 1 >= totalPages} onClick={() => load(page + 1)}>Next</Button>
        </div>
      </div>
      <TransactionDialog
        open={open}
        editing={editing}
        accounts={accounts}
        categories={categories}
        onOpenChange={setOpen}
        onSaved={() => { setOpen(false); load(0).catch((err) => setError(err.message)); }}
      />
    </div>
  );
}

function TransactionDialog({
  open, editing, accounts, categories, onOpenChange, onSaved,
}: {
  open: boolean;
  editing: Transaction | null;
  accounts: Account[];
  categories: Category[];
  onOpenChange: (open: boolean) => void;
  onSaved: () => void;
}) {
  const form = useForm<FormValues>({ resolver: zodResolver(schema) });
  const [error, setError] = useState<string | null>(null);
  const type = form.watch("type");

  useEffect(() => {
    if (!open) return;
    form.reset(editing ? {
      type: editing.type,
      amount: editing.amount,
      transactionDate: editing.transactionDate,
      accountId: String(editing.accountId),
      transferAccountId: editing.transferAccountId ? String(editing.transferAccountId) : "",
      categoryId: editing.categoryId ? String(editing.categoryId) : "",
      description: editing.description || "",
      notes: editing.notes || "",
    } : {
      type: "EXPENSE",
      amount: "",
      transactionDate: new Date().toISOString().slice(0, 10),
      accountId: "",
      transferAccountId: "",
      categoryId: "",
      description: "",
      notes: "",
    });
  }, [open, editing, form]);

  async function onSubmit(values: FormValues) {
    setError(null);
    const body = {
      type: values.type,
      amount: values.amount,
      transactionDate: values.transactionDate,
      accountId: Number(values.accountId),
      transferAccountId: values.type === "TRANSFER" ? Number(values.transferAccountId) : null,
      categoryId: values.type === "INCOME" || values.type === "EXPENSE" ? Number(values.categoryId) : null,
      description: values.description || null,
      notes: values.notes || null,
    };
    try {
      if (editing) await transactionApi.update(editing.id, body);
      else await transactionApi.create(body);
      onSaved();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save");
    }
  }

  const categoryType = type === "INCOME" ? "INCOME" : "EXPENSE";

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{editing ? "Edit transaction" : "Add transaction"}</DialogTitle>
        </DialogHeader>
        <form className="space-y-3" onSubmit={form.handleSubmit(onSubmit)}>
          <ErrorText message={error} />
          <Field label="Type" error={form.formState.errors.type?.message}>
            <select className="h-10 w-full rounded-md border bg-white px-3 text-sm" {...form.register("type")}>
              <option value="EXPENSE">Expense</option>
              <option value="INCOME">Income</option>
              <option value="TRANSFER">Transfer</option>
              <option value="LOAN_PAYMENT">Loan payment</option>
            </select>
          </Field>
          <Field label="Amount (INR)" error={form.formState.errors.amount?.message}>
            <Input inputMode="decimal" placeholder="1200.00" {...form.register("amount")} />
          </Field>
          <Field label="Date" error={form.formState.errors.transactionDate?.message}>
            <Input type="date" {...form.register("transactionDate")} />
          </Field>
          <Field label={type === "TRANSFER" ? "From account" : "Account"} error={form.formState.errors.accountId?.message}>
            <select className="h-10 w-full rounded-md border bg-white px-3 text-sm" {...form.register("accountId")}>
              <option value="">Select account</option>
              {accounts.map((account) => <option key={account.id} value={account.id}>{account.name}</option>)}
            </select>
          </Field>
          {type === "TRANSFER" && (
            <Field label="To account">
              <select className="h-10 w-full rounded-md border bg-white px-3 text-sm" {...form.register("transferAccountId")}>
                <option value="">Select account</option>
                {accounts.map((account) => <option key={account.id} value={account.id}>{account.name}</option>)}
              </select>
            </Field>
          )}
          {(type === "INCOME" || type === "EXPENSE") && (
            <Field label="Category">
              <select className="h-10 w-full rounded-md border bg-white px-3 text-sm" {...form.register("categoryId")}>
                <option value="">Select category</option>
                {categories.filter((category) => category.type === categoryType).map((category) => (
                  <option key={category.id} value={category.id}>{category.name}</option>
                ))}
              </select>
            </Field>
          )}
          <Field label="Description">
            <Input {...form.register("description")} />
          </Field>
          <Button type="submit" disabled={form.formState.isSubmitting}>Save</Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}
