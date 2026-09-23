"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { MONTHS, currentPeriod } from "@/lib/format";

export function usePeriod() {
  const params = useSearchParams();
  const fallback = currentPeriod();
  const year = Number(params.get("year") || fallback.year);
  const month = Number(params.get("month") || fallback.month);
  return { year, month };
}

export function MonthPicker() {
  const { year, month } = usePeriod();
  const params = useSearchParams();
  const pathname = usePathname();
  const router = useRouter();

  function update(nextYear: number, nextMonth: number) {
    const query = new URLSearchParams(params.toString());
    query.set("year", String(nextYear));
    query.set("month", String(nextMonth));
    router.replace(`${pathname}?${query.toString()}`);
  }

  return (
    <div className="flex gap-2">
      <label className="sr-only" htmlFor="month">Month</label>
      <select
        id="month"
        className="h-10 rounded-md border bg-white px-3 text-sm"
        value={month}
        onChange={(event) => update(year, Number(event.target.value))}
      >
        {MONTHS.map((name, index) => (
          <option key={name} value={index + 1}>{name}</option>
        ))}
      </select>
      <label className="sr-only" htmlFor="year">Year</label>
      <input
        id="year"
        type="number"
        min={2000}
        max={2100}
        className="h-10 w-24 rounded-md border bg-white px-3 text-sm"
        value={year}
        onChange={(event) => update(Number(event.target.value), month)}
      />
    </div>
  );
}
