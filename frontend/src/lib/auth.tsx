"use client";

import { createContext, useContext, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { authApi, getToken, setToken } from "./api";
import type { User } from "./types";

type AuthState = {
  user: User | null;
  loading: boolean;
  setSession: (token: string, user: User) => void;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const router = useRouter();

  useEffect(() => {
    const token = getToken();
    if (!token) {
      setLoading(false);
      return;
    }
    authApi.me().then(setUser).catch(() => setToken(null)).finally(() => setLoading(false));
  }, []);

  function setSession(token: string, next: User) {
    setToken(token);
    setUser(next);
  }

  async function logout() {
    try {
      await authApi.logout();
    } catch {
      /* token may already be invalid */
    }
    setToken(null);
    setUser(null);
    router.push("/login");
  }

  return <AuthContext.Provider value={{ user, loading, setSession, logout }}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth must be used within AuthProvider");
  return value;
}
