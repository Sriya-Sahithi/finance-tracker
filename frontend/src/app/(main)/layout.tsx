import { Suspense } from "react";
import { Shell } from "@/components/shell";

export default function MainLayout({ children }: { children: React.ReactNode }) {
  return (
    <Shell>
      <Suspense fallback={<p className="text-sm text-muted-foreground">Loading…</p>}>{children}</Suspense>
    </Shell>
  );
}
