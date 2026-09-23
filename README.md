# Finance Tracker

Personal finance app for accounts, transactions, monthly budgets, and reducing-balance loans. Currency is INR and dates use Asia/Kolkata. Amounts are stored as decimals with no currency symbol, so another currency can be added later without rewriting the ledger.

## Architecture

One Spring Boot monolith. The Next.js app talks to it over HTTP with a JWT.

```
browser → Next.js (Render) → Spring Boot API (Render) → PostgreSQL (Render)
```

Backend packages follow controller → service → repository → database:

`auth`, `user`, `account`, `category`, `transaction`, `budget`, `loan`, `dashboard`, `report`, `common`.

Controllers return DTOs. JPA entities stay inside the service layer. `@RestControllerAdvice` returns one error shape and never includes a stack trace.

Loan math lives in `LoanCalculationService`. EMI uses the reducing-balance formula

```
EMI = P × r × (1+r)^n / ((1+r)^n − 1)
```

where `r` is the monthly rate and `n` is the number of months. Zero-interest loans use `P / n`. The final installment is adjusted so the balance is exactly zero. Prepayments keep the EMI and shorten the tenure (`REDUCE_TENURE`). The strategy enum is the place to add a reduce-EMI option later.

Transfers update both account balances and are excluded from income, expenses, savings, and budgets.

## Prerequisites

- Java 21
- Maven 3.9+
- Node.js 22
- Docker, for local PostgreSQL via Compose and for Testcontainers

## Local setup

Start PostgreSQL:

```bash
docker compose up -d
```

The Compose file creates database `finance_tracker` with user `finance` and password `finance`.

### Environment variables

Backend local defaults live in `backend/src/main/resources/application-dev.yml`. Override them when you need to:

| Variable | Used by | Purpose |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | API | `dev` locally, `prod` on Render |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | API dev | Local Postgres connection |
| `PORT` | API | HTTP port, defaults to 8080. Render sets this. The API binds `0.0.0.0`. |
| `DATABASE_URL` | API prod | JDBC URL (`jdbc:postgresql://host:5432/finance_tracker`) or Render's `postgresql://` / `postgres://` URL. User and password may be embedded. |
| `DATABASE_USERNAME` | API prod | Database user. Optional when `DATABASE_URL` already contains the user. |
| `DATABASE_PASSWORD` | API prod | Database password. Optional when `DATABASE_URL` already contains the password. |
| `JWT_SECRET` | API | HMAC secret, at least 32 characters. Required in production. The Blueprint generates one. |
| `JWT_EXPIRATION_MS` | API | Token lifetime, default 24 hours |
| `CORS_ALLOWED_ORIGINS` | API | Comma-separated browser origins, no trailing slash. On Render this is the site's public URL. |
| `NEXT_PUBLIC_API_URL` | Web | API origin with no path, default `http://localhost:8080`. Next.js inlines it at build time. On Render this is the API's public URL. |

Copy `frontend/.env.example` to `frontend/.env.local` if the API is not on port 8080.

A Render internal URL such as `postgresql://user:password@host:5432/finance_tracker` is rewritten to `jdbc:postgresql://host:5432/finance_tracker` before Flyway and the pool start. Query parameters, including `sslmode=require` on an external URL, are kept. Do not commit secrets.

### Run the API

```bash
cd backend
mvn spring-boot:run
```

The dev profile is the default. Flyway applies `backend/src/main/resources/db/migration/V1__create_users.sql` through `V8__add_statement_imports.sql` on startup. Production sets `ddl-auto` to `none`, so Hibernate does not create or update tables.

- Health: `GET /actuator/health`
- OpenAPI: `GET /v3/api-docs`
- Swagger UI: `GET /swagger-ui.html`

### Run the web app

Docker is not required for the frontend.

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:3000, register, and use the sidebar: Dashboard, Transactions, Budgets, Loans, Accounts, Reports, Settings.

## Tests

```bash
cd backend
mvn test
```

Unit tests cover EMI (standard rate, zero interest, other rates and tenures, large principal, final installment, prepayment, full payoff), budget usage (under, equal, over), and account balance effects (income, expense, transfer).

`@SpringBootTest` tests use Testcontainers PostgreSQL. They check migrations, auth, user isolation, transaction balances, budgets by month, loan schedule, prepayment, and the monthly report. A user receives 404 for another user's accounts, transactions, budgets, and loans.

## API

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/api/auth/register`, `/api/auth/login` | Public. Passwords are BCrypt hashed and never returned. |
| POST | `/api/auth/logout` | Invalidates the current JWT |
| GET, PATCH | `/api/users/me` | Current profile |
| GET, POST | `/api/accounts`, `/api/accounts/{id}` | PUT and DELETE as well |
| GET, POST | `/api/categories`, `/api/categories/{id}` | PUT and DELETE as well |
| GET, POST | `/api/transactions`, `/api/transactions/{id}` | Page, search, category, account, date range, type |
| POST | `/api/statement-imports/preview` | Multipart CSV upload. Returns a reviewable preview only. |
| POST | `/api/statement-imports/{sessionId}/confirm` | Imports selected preview rows into a chosen existing account. |
| GET, POST | `/api/budgets`, `/api/budgets/{id}` | Monthly expense budgets |
| GET, POST | `/api/loans`, `/api/loans/{id}` | EMI calculated on the server |
| GET | `/api/loans/{id}/schedule` | Actual payments plus projected rows |
| GET, POST | `/api/loans/{id}/payments` | EMI plus optional extra principal |
| POST | `/api/loans/{id}/prepayment` | Interest saved, new payoff, EMIs reduced |
| GET | `/api/dashboard?year=&month=` | Income, expenses, savings, budget, loans |
| GET | `/api/reports/monthly?year=&month=` | Series for the charts |

Amounts in JSON are decimal strings such as `"1200.00"`. The UI formats them as `₹1,20,000.00`.

Every query is scoped by the authenticated user id.


## CSV bank-statement import

The initial import feature is intentionally scoped to **CSV bank statements only**. It does not parse PDFs or CIBIL reports.

### Supported CSV shape

- Header row is required
- Supported date headers: `Date`, `Transaction Date`, `Txn Date`, `Value Date`, `Posting Date`
- Supported description headers: `Description`, `Narration`, `Particulars`, `Details`, `Remarks`
- Supported amount headers: `Debit`, `Credit`, `Amount`
- Supported transaction-type headers: `Transaction Type`, `Type`, `DR/CR`
- Supported reference headers: `Reference`, `Ref`, `Ref No`, `Cheque No`, `UTR`, `Transaction ID`
- Supported account metadata headers: `Account Name`, `Account Number`, `Account No`, `Acct No`
- Dates are parsed deterministically as `yyyy-MM-dd`, `dd/MM/yyyy`, `dd-MM-yyyy`, `yyyy/MM/dd`, or `dd/MM/yy`
- The preview detects account name/account number metadata when present and suggests a matching existing account, but confirmation still requires the user to explicitly choose the destination account

Example CSV:

```csv
Date,Narration,Debit,Credit,Reference,Account Number,Account Name
2026-03-01,Coffee,120.50,,UPI-1,1234567890,HDFC Savings
2026-03-02,Salary,,2000.00,NEFT-9,1234567890,HDFC Savings
```

Import behavior:

- Preview does **not** mutate balances or create transactions
- Confirmation imports only the selected valid rows
- Imported rows are stored as income or expense transactions using the user's existing income/expense categories (preferring `Other Income` / `Other Expense`)
- Duplicate protection uses a stable row fingerprint based on the detected account metadata plus normalized row values; re-confirming the same row is skipped instead of creating a duplicate transaction
- The uploaded file must be a `.csv` file and must be 1 MB or smaller

## Deployment

`render.yaml` is the Blueprint. It hosts the whole app on Render. Nothing deploys until you apply that file.

It creates three resources, all in Oregon:

- Web service `finance-tracker-api` — Docker, `backend/Dockerfile`, context `backend`, health check `/actuator/health`
- Postgres `finance-tracker-db` — database `finance_tracker`, Postgres 16
- Web service `finance-tracker-web` — Node 22, root directory `frontend`, build `npm ci && npm run build`, start `npm start -- --hostname 0.0.0.0 --port $PORT`

Plans are omitted, so Render's defaults apply: each web service `starter`, database `basic-256mb`. Those are paid instance types.

1. In the Render Dashboard, choose **New** → **Blueprint**.
2. Connect GitHub repo `Viswanadh-Ganti/finance-tracker`, branch `main`.
3. Render reads `render.yaml` at the repo root. Apply it. There is no prompt for URLs or secrets.

The Blueprint wires the public URLs. You do not create the API first and type them in later:

- `NEXT_PUBLIC_API_URL` is the API service's `RENDER_EXTERNAL_URL`
- `CORS_ALLOWED_ORIGINS` is the site's `RENDER_EXTERNAL_URL`

If the service names are available, those URLs are `https://finance-tracker-api.onrender.com` and `https://finance-tracker-web.onrender.com`. A name collision makes Render add a suffix; the `fromService` links still follow the real hosts.

`NEXT_PUBLIC_API_URL` is compiled into the browser bundle. If the first site build ran before that value was set, open `finance-tracker-web` and deploy it again after the API URL is visible. `CORS_ALLOWED_ORIGINS` is read when the API process starts. If the API booted with an empty origin list, deploy `finance-tracker-api` again.

`DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` come from the Postgres instance. The API accepts Render's `postgresql://` URL and a `jdbc:postgresql://` URL. `JWT_SECRET` is generated (base64, 256 bits, longer than 32 characters). `SPRING_PROFILES_ACTIVE=prod` is set in the Blueprint. Render sets `PORT` on both web services. Flyway runs on boot. Do not enable Hibernate `ddl-auto` in production.

Confirm the API:

```bash
curl -fsS https://finance-tracker-api.onrender.com/actuator/health
```

A live service returns HTTP 200 and `{"status":"UP"}`. Use the dashboard host if Render added a suffix. Then open the site URL and register.

### GitHub

Source of truth for the API, the site, and Postgres. Do not commit `.env.local`, JWT secrets, or database passwords.
