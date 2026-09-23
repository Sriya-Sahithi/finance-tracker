"use client";

import { useEffect, useState } from "react";
import { budgetApi, categoryApi } from "@/lib/api";
import { formatInr } from "@/lib/format";
import type { Budget, Category } from "@/lib/types";
import { MonthPicker, usePeriod } from "@/components/month-picker";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Empty, ErrorText } from "@/components/feedback";
import { Field } from "@/components/auth-card";

export default function BudgetsPage() {
  const { year, month } = usePeriod();
  const [rows, setRows] = useState<Budget[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<Budget | null>(null);
  const [categoryId, setCategoryId] = useState("");
  const [amount, setAmount] = useState("");

  function load() {
    budgetApi.list(year, month).then(setRows).catch((err) => setError(err.message));
  }

  useEffect(() => {
    categoryApi.list("EXPENSE").then(setCategories).catch((err) => setError(err.message));
  }, []);

  useEffect(() => { load(); }, [year, month]);

  async function save() {
    setError(null);
    const body = { categoryId: Number(editing ? editing.categoryId : categoryId), year, month, amount };
    try {
      if (editing) await budgetApi.update(editing.id, body);
      else await budgetApi.create(body);
      setOpen(false);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save budget");
    }
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Budgets</h1>
          <p className="text-sm text-muted-foreground">Monthly limits for expense categories. Over-budget rows are highlighted.</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <MonthPicker />
          <Button type="button" onClick={() => { setEditing(null); setAmount(""); setCategoryId(""); setOpen(true); }}>Add budget</Button>
        </div>
      </div>
      <ErrorText message={error} />
      {rows.length === 0 ? <Empty title="No budgets this month" body="Set a limit for an expense category to track usage." /> : (
        <div className="overflow-x-auto rounded-lg border bg-white">
          <table className="w-full text-sm">
            <thead className="border-b text-left text-muted-foreground">
              <tr>
                <th className="px-4 py-3 font-medium">Category</th>
                <th className="px-4 py-3 text-right font-medium">Budget</th>
                <th className="px-4 py-3 text-right font-medium">Spent</th>
                <th className="px-4 py-3 text-right font-medium">Remaining</th>
                <th className="px-4 py-3 font-medium">Usage</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => {
                const percent = Math.min(Number(row.usagePercent), 100);
                return (
                  <tr key={row.id} className={row.overBudget ? "bg-rose-50" : ""}>
                    <td className="px-4 py-3 font-medium">{row.categoryName}</td>
                    <td className="tabular px-4 py-3 text-right">{formatInr(row.amount)}</td>
                    <td className="tabular px-4 py-3 text-right">{formatInr(row.spent)}</td>
                    <td className={`tabular px-4 py-3 text-right ${row.overBudget ? "text-rose-700" : ""}`}>{formatInr(row.remaining)}</td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <div className="h-2 w-28 overflow-hidden rounded-full bg-slate-100" role="progressbar" aria-valuenow={Number(row.usagePercent)} aria-valuemin={0} aria-valuemax={100}>
                          <div className={`h-full ${row.overBudget ? "bg-rose-500" : "bg-teal-700"}`} style={{ width: `${percent}%` }} />
                        </div>
                        <span className="tabular text-xs">{row.usagePercent}%</span>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-right">
                      <Button type="button" size="sm" variant="outline" onClick={() => { setEditing(row); setAmount(row.amount); setOpen(true); }}>Edit</Button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>{editing ? "Edit budget" : "Add budget"}</DialogTitle></DialogHeader>
          <div className="space-y-3">
            <Field label="Category">
              <select className="h-10 w-full rounded-md border bg-white px-3 text-sm" value={editing ? String(editing.categoryId) : categoryId} disabled={Boolean(editing)} onChange={(event) => setCategoryId(event.target.value)}>
                <option value="">Select category</option>
                {categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
              </select>
            </Field>
            <Field label="Amount (INR)">
              <Input inputMode="decimal" value={amount} onChange={(event) => setAmount(event.target.value)} placeholder="8000.00" />
            </Field>
            <Button type="button" onClick={save}>Save</Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
