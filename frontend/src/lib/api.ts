import type {
  Account,
  ApiFieldError,
  Budget,
  Category,
  Dashboard,
  Loan,
  LoanPayment,
  MonthlyReport,
  Page,
  Prepayment,
  ScheduleRow,
  StatementImportConfirmResponse,
  StatementImportPreview,
  Transaction,
  User,
} from "./types";

const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export class ApiError extends Error {
  status: number;
  fieldErrors: ApiFieldError[];

  constructor(status: number, message: string, fieldErrors: ApiFieldError[] = []) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

export function getToken() {
  if (typeof window === "undefined") return null;
  return localStorage.getItem("finance-tracker.token");
}

export function setToken(token: string | null) {
  if (token) localStorage.setItem("finance-tracker.token", token);
  else localStorage.removeItem("finance-tracker.token");
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  const isFormData = typeof FormData !== "undefined" && options.body instanceof FormData;
  if (options.body && !isFormData && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  const token = getToken();
  if (token) headers.set("Authorization", "Bearer " + token);
  const response = await fetch(`${API_URL}${path}`, { ...options, headers });
  if (response.status === 204) return undefined as T;
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new ApiError(response.status, body.message || "Request failed", body.fieldErrors || []);
  }
  return body as T;
}

export const authApi = {
  register: (body: { email: string; password: string; name: string }) =>
    api<{ token: string; user: User }>("/api/auth/register", { method: "POST", body: JSON.stringify(body) }),
  login: (body: { email: string; password: string }) =>
    api<{ token: string; user: User }>("/api/auth/login", { method: "POST", body: JSON.stringify(body) }),
  logout: () => api<void>("/api/auth/logout", { method: "POST" }),
  me: () => api<User>("/api/users/me"),
  updateMe: (name: string) => api<User>("/api/users/me", { method: "PATCH", body: JSON.stringify({ name }) }),
};

export const accountApi = {
  list: () => api<Account[]>("/api/accounts"),
  get: (id: number) => api<{ account: Account; recentTransactions: Transaction[] }>(`/api/accounts/${id}`),
  create: (body: unknown) => api<Account>("/api/accounts", { method: "POST", body: JSON.stringify(body) }),
  update: (id: number, body: unknown) => api<Account>(`/api/accounts/${id}`, { method: "PUT", body: JSON.stringify(body) }),
  remove: (id: number) => api<void>(`/api/accounts/${id}`, { method: "DELETE" }),
};

export const categoryApi = {
  list: (type?: string) => api<Category[]>(`/api/categories${type ? `?type=${type}` : ""}`),
  create: (body: unknown) => api<Category>("/api/categories", { method: "POST", body: JSON.stringify(body) }),
  update: (id: number, body: unknown) => api<Category>(`/api/categories/${id}`, { method: "PUT", body: JSON.stringify(body) }),
  remove: (id: number) => api<void>(`/api/categories/${id}`, { method: "DELETE" }),
};

export const transactionApi = {
  list: (query: string) => api<Page<Transaction>>(`/api/transactions${query}`),
  create: (body: unknown) => api<Transaction>("/api/transactions", { method: "POST", body: JSON.stringify(body) }),
  update: (id: number, body: unknown) =>
    api<Transaction>(`/api/transactions/${id}`, { method: "PUT", body: JSON.stringify(body) }),
  remove: (id: number) => api<void>(`/api/transactions/${id}`, { method: "DELETE" }),
};

export const statementImportApi = {
  preview: (file: File) => {
    const body = new FormData();
    body.append("file", file);
    return api<StatementImportPreview>("/api/statement-imports/preview", { method: "POST", body });
  },
  confirm: (sessionId: string, body: { accountId: number; rowFingerprints: string[] }) =>
    api<StatementImportConfirmResponse>(`/api/statement-imports/${sessionId}/confirm`, {
      method: "POST",
      body: JSON.stringify(body),
    }),
};

export const budgetApi = {
  list: (year: number, month: number) => api<Budget[]>(`/api/budgets?year=${year}&month=${month}`),
  create: (body: unknown) => api<Budget>("/api/budgets", { method: "POST", body: JSON.stringify(body) }),
  update: (id: number, body: unknown) => api<Budget>(`/api/budgets/${id}`, { method: "PUT", body: JSON.stringify(body) }),
  remove: (id: number) => api<void>(`/api/budgets/${id}`, { method: "DELETE" }),
};

export const loanApi = {
  list: () => api<Loan[]>("/api/loans"),
  get: (id: number) => api<Loan>(`/api/loans/${id}`),
  create: (body: unknown) => api<Loan>("/api/loans", { method: "POST", body: JSON.stringify(body) }),
  update: (id: number, body: unknown) => api<Loan>(`/api/loans/${id}`, { method: "PUT", body: JSON.stringify(body) }),
  remove: (id: number) => api<void>(`/api/loans/${id}`, { method: "DELETE" }),
  schedule: (id: number) => api<{ loanId: number; emiAmount: string; schedule: ScheduleRow[] }>(`/api/loans/${id}/schedule`),
  payments: (id: number) => api<LoanPayment[]>(`/api/loans/${id}/payments`),
  pay: (id: number, body: unknown) =>
    api<LoanPayment>(`/api/loans/${id}/payments`, { method: "POST", body: JSON.stringify(body) }),
  prepay: (id: number, body: unknown) =>
    api<Prepayment>(`/api/loans/${id}/prepayment`, { method: "POST", body: JSON.stringify(body) }),
};

export const dashboardApi = {
  get: (year: number, month: number) => api<Dashboard>(`/api/dashboard?year=${year}&month=${month}`),
};

export const reportApi = {
  monthly: (year: number, month: number) => api<MonthlyReport>(`/api/reports/monthly?year=${year}&month=${month}`),
};
