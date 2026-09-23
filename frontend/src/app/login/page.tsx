"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { ApiError, authApi } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ErrorText } from "@/components/feedback";
import { AuthCard, Field } from "@/components/auth-card";
import { useState } from "react";

const schema = z.object({
  email: z.string().email("Enter a valid email"),
  password: z.string().min(1, "Password is required"),
});

type FormValues = z.infer<typeof schema>;

export default function LoginPage() {
  const router = useRouter();
  const { setSession } = useAuth();
  const [error, setError] = useState<string | null>(null);
  const form = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { email: "", password: "" } });

  async function onSubmit(values: FormValues) {
    setError(null);
    try {
      const result = await authApi.login(values);
      setSession(result.token, result.user);
      router.push("/dashboard");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not log in");
    }
  }

  return (
    <AuthCard title="Log in" subtitle="Track spending, budgets, and loans in INR.">
      <form className="space-y-4" onSubmit={form.handleSubmit(onSubmit)} noValidate>
        <ErrorText message={error} />
        <Field label="Email" error={form.formState.errors.email?.message}>
          <Input type="email" autoComplete="email" {...form.register("email")} />
        </Field>
        <Field label="Password" error={form.formState.errors.password?.message}>
          <Input type="password" autoComplete="current-password" {...form.register("password")} />
        </Field>
        <Button className="w-full" type="submit" disabled={form.formState.isSubmitting}>Log in</Button>
      </form>
      <p className="mt-4 text-sm text-muted-foreground">
        New here? <Link className="font-medium text-primary" href="/register">Create an account</Link>
      </p>
    </AuthCard>
  );
}

