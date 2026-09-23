"use client";

import { useState } from "react";
import { importApi } from "@/lib/api";
import { formatInr } from "@/lib/format";
import type { ImportTargetType, StatementColumnMapping, StatementParsePreview } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { ErrorText } from "@/components/feedback";
import { Field } from "@/components/auth-card";

const MAPPING_FIELDS: { key: keyof StatementColumnMapping; label: string; required?: boolean }[] = [
  { key: "dateColumn", label: "Transaction date", required: true },
  { key: "descriptionColumn", label: "Description / narration" },
  { key: "amountColumn", label: "Amount (signed)" },
  { key: "debitColumn", label: "Debit / withdrawal" },
  { key: "creditColumn", label: "Credit / deposit" },
  { key: "balanceColumn", label: "Balance / outstanding" },
  { key: "accountNumberColumn", label: "Account number" },
];

export function StatementUploadDialog({
  targetType,
  targets,
  triggerLabel,
  onImported,
}: {
  targetType: ImportTargetType;
  targets: { id: number; label: string }[];
  triggerLabel: string;
  onImported: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [targetId, setTargetId] = useState<string>(targets[0] ? String(targets[0].id) : "");
  const [preview, setPreview] = useState<StatementParsePreview | null>(null);
  const [mapping, setMapping] = useState<StatementColumnMapping | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  function reset() {
    setFile(null);
    setPreview(null);
    setMapping(null);
    setError(null);
    setSuccess(null);
  }

  async function loadPreview() {
    if (!file) {
      setError("Choose a .csv or .xlsx file first");
      return;
    }
    setError(null);
    setLoading(true);
    try {
      const result = await importApi.previewStatement(file, targetType);
      setPreview(result);
      setMapping(result.suggestedMapping);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not parse file");
    } finally {
      setLoading(false);
    }
  }

  async function confirmImport() {
    if (!preview || !mapping || !targetId) return;
    setError(null);
    setLoading(true);
    try {
      const result = await importApi.commitStatement({
        importToken: preview.importToken,
        targetType,
        targetId: Number(targetId),
        mapping,
      });
      setSuccess(
        `Imported ${result.transactionsCreated} transaction(s) from ${result.rowsProcessed} row(s). ` +
          `${result.rowsSkipped} row(s) skipped. New balance: ${formatInr(result.updatedBalance)}.`,
      );
      onImported();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not import file");
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <Button type="button" variant="outline" onClick={() => setOpen(true)}>
        {triggerLabel}
      </Button>
      <Dialog
        open={open}
        onOpenChange={(next) => {
          setOpen(next);
          if (!next) reset();
        }}
      >
      <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{triggerLabel}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <ErrorText message={error} />
          {success && <p className="rounded-md bg-teal-50 px-3 py-2 text-sm text-teal-800">{success}</p>}

          {!preview && (
            <div className="space-y-3">
              <Field label={targetType === "ACCOUNT" ? "Account" : "Loan"}>
                <select
                  className="h-10 w-full rounded-md border bg-white px-3 text-sm"
                  value={targetId}
                  onChange={(event) => setTargetId(event.target.value)}
                >
                  {targets.map((target) => (
                    <option key={target.id} value={target.id}>
                      {target.label}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Statement file (.csv or .xlsx)">
                <Input
                  type="file"
                  accept=".csv,.xlsx"
                  onChange={(event) => setFile(event.target.files?.[0] ?? null)}
                />
              </Field>
              <Button type="button" onClick={loadPreview} disabled={loading}>
                {loading ? "Parsing…" : "Preview"}
              </Button>
            </div>
          )}

          {preview && !success && (
            <div className="space-y-4">
              <p className="text-sm text-muted-foreground">
                {preview.rowCount} row(s) detected. Confirm the column mapping below before importing.
              </p>
              <div className="grid gap-3 sm:grid-cols-2">
                {MAPPING_FIELDS.map((field) => (
                  <Field key={field.key} label={field.label + (field.required ? " *" : "")}>
                    <select
                      className="h-10 w-full rounded-md border bg-white px-3 text-sm"
                      value={mapping?.[field.key] ?? ""}
                      onChange={(event) =>
                        setMapping((current) =>
                          current ? { ...current, [field.key]: event.target.value || null } : current,
                        )
                      }
                    >
                      <option value="">Not mapped</option>
                      {preview.headers.map((header) => (
                        <option key={header} value={header}>
                          {header}
                        </option>
                      ))}
                    </select>
                  </Field>
                ))}
              </div>
              <div className="overflow-x-auto rounded-md border">
                <table className="w-full text-left text-xs">
                  <thead className="bg-muted">
                    <tr>
                      {preview.headers.map((header) => (
                        <th key={header} className="px-2 py-1 font-medium">
                          {header}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {preview.sampleRows.map((row, index) => (
                      <tr key={index} className="border-t">
                        {preview.headers.map((header) => (
                          <td key={header} className="px-2 py-1">
                            {row[header]}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="flex gap-2">
                <Button type="button" onClick={confirmImport} disabled={loading}>
                  {loading ? "Importing…" : "Confirm & import"}
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
