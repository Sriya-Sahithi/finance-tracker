CREATE TABLE loans (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    name VARCHAR(160) NOT NULL,
    loan_type VARCHAR(32) NOT NULL,
    principal_amount NUMERIC(19, 2) NOT NULL,
    outstanding_principal NUMERIC(19, 2) NOT NULL,
    annual_interest_rate NUMERIC(7, 4) NOT NULL,
    tenure_months INTEGER NOT NULL,
    emi_amount NUMERIC(19, 2) NOT NULL,
    start_date DATE NOT NULL,
    first_payment_date DATE NOT NULL,
    payment_due_day INTEGER NOT NULL,
    prepayment_strategy VARCHAR(32) NOT NULL DEFAULT 'REDUCE_TENURE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_loans_type CHECK (loan_type IN ('HOME', 'PERSONAL', 'AUTO', 'EDUCATION', 'OTHER')),
    CONSTRAINT ck_loans_principal CHECK (principal_amount > 0),
    CONSTRAINT ck_loans_outstanding CHECK (outstanding_principal >= 0),
    CONSTRAINT ck_loans_rate CHECK (annual_interest_rate >= 0),
    CONSTRAINT ck_loans_tenure CHECK (tenure_months > 0),
    CONSTRAINT ck_loans_emi CHECK (emi_amount > 0),
    CONSTRAINT ck_loans_due_day CHECK (payment_due_day BETWEEN 1 AND 28),
    CONSTRAINT ck_loans_strategy CHECK (prepayment_strategy IN ('REDUCE_TENURE'))
);

CREATE INDEX idx_loans_user_id ON loans (user_id);

ALTER TABLE transactions
    ADD COLUMN loan_id BIGINT REFERENCES loans (id);

CREATE INDEX idx_transactions_loan_id ON transactions (loan_id);
