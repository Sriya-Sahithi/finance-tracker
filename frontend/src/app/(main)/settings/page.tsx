"use client";

import { useState } from "react";
import { authApi } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ErrorText } from "@/components/feedback";
import { Field } from "@/components/auth-card";

export default function SettingsPage() {
  const { user, setSession, logout } = useAuth();
  const [name, setName] = useState(user?.name || "");
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function save() {
    setError(null);
    setMessage(null);
    try {
      const updated = await authApi.updateMe(name);
      const token = localStorage.getItem("finance-tracker.token") || "";
      setSession(token, updated);
      setMessage("Name updated.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update profile");
    }
  }

  return (
    <div className="max-w-lg space-y-5">
      <div>
        <h1 className="text-2xl font-semibold">Settings</h1>
        <p className="text-sm text-muted-foreground">Profile, currency, and timezone.</p>
      </div>
      <ErrorText message={error} />
      {message && <p className="rounded-md bg-teal-50 px-3 py-2 text-sm text-teal-800">{message}</p>}
      <section className="space-y-3 rounded-lg border bg-white p-5">
        <Field label="Name"><Input value={name} onChange={(event) => setName(event.target.value)} /></Field>
        <Field label="Email"><Input value={user?.email || ""} disabled /></Field>
        <Button type="button" onClick={save}>Save name</Button>
      </section>
      <section className="rounded-lg border bg-white p-5 text-sm">
        <p><span className="text-muted-foreground">Currency:</span> INR (₹)</p>
        <p className="mt-2"><span className="text-muted-foreground">Timezone:</span> Asia/Kolkata</p>
        <p className="mt-2 text-muted-foreground">Amounts are stored without a currency symbol so additional currencies can be added later. Only INR is accepted today.</p>
      </section>
      <Button type="button" variant="outline" onClick={logout}>Log out</Button>
    </div>
  );
}
