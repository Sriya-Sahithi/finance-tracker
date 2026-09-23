"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import {
  Landmark,
  LayoutDashboard,
  Menu,
  PieChart,
  Receipt,
  Settings,
  Wallet,
  X,
} from "lucide-react";
import { useAuth } from "@/lib/auth";
import { cn } from "@/lib/utils";

const links = [
  { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { href: "/transactions", label: "Transactions", icon: Receipt },
  { href: "/budgets", label: "Budgets", icon: PieChart },
  { href: "/loans", label: "Loans", icon: Landmark },
  { href: "/accounts", label: "Accounts", icon: Wallet },
  { href: "/reports", label: "Reports", icon: PieChart },
  { href: "/settings", label: "Settings", icon: Settings },
];

export function Shell({ children }: { children: React.ReactNode }) {
  const { user, loading, logout } = useAuth();
  const pathname = usePathname();
  const router = useRouter();
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!loading && !user) router.replace("/login");
  }, [loading, user, router]);

  useEffect(() => setOpen(false), [pathname]);

  if (loading || !user) {
    return <div className="grid min-h-screen place-items-center text-sm text-muted-foreground">Loading…</div>;
  }

  const nav = (
    <nav className="flex flex-1 flex-col gap-1" aria-label="Primary">
      {links.map((link) => {
        const active = pathname === link.href || pathname.startsWith(`${link.href}/`);
        const Icon = link.icon;
        return (
          <Link
            key={link.href}
            href={link.href}
            className={cn(
              "flex items-center gap-3 rounded-md px-3 py-2 text-sm",
              active ? "bg-white/10 text-white" : "text-slate-300 hover:bg-white/5 hover:text-white",
            )}
            aria-current={active ? "page" : undefined}
          >
            <Icon className="h-4 w-4" aria-hidden />
            {link.label}
          </Link>
        );
      })}
    </nav>
  );

  return (
    <div className="min-h-screen md:grid md:grid-cols-[240px_1fr]">
      <aside className="hidden bg-slate-950 text-slate-100 md:flex md:flex-col md:p-4">
        <Link href="/dashboard" className="mb-6 px-3 text-lg font-semibold tracking-tight">
          Finance Tracker
        </Link>
        {nav}
        <button type="button" onClick={logout} className="mt-4 rounded-md px-3 py-2 text-left text-sm text-slate-300 hover:bg-white/5">
          Log out
        </button>
      </aside>
      <div>
        <header className="flex items-center justify-between border-b bg-white px-4 py-3 md:hidden">
          <button type="button" aria-label="Open menu" onClick={() => setOpen(true)}>
            <Menu className="h-5 w-5" />
          </button>
          <span className="font-semibold">Finance Tracker</span>
          <span className="text-sm text-muted-foreground">{user.name.split(" ")[0]}</span>
        </header>
        {open && (
          <div className="fixed inset-0 z-50 md:hidden">
            <button className="absolute inset-0 bg-slate-950/50" aria-label="Close menu" onClick={() => setOpen(false)} />
            <div className="relative flex h-full w-72 flex-col bg-slate-950 p-4 text-slate-100">
              <div className="mb-4 flex items-center justify-between px-3">
                <span className="font-semibold">Finance Tracker</span>
                <button type="button" aria-label="Close menu" onClick={() => setOpen(false)}>
                  <X className="h-5 w-5" />
                </button>
              </div>
              {nav}
              <button type="button" onClick={logout} className="mt-4 rounded-md px-3 py-2 text-left text-sm text-slate-300">
                Log out
              </button>
            </div>
          </div>
        )}
        <main className="mx-auto max-w-6xl px-4 py-6 md:px-8">{children}</main>
      </div>
    </div>
  );
}
