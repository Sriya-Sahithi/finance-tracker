ALTER TABLE accounts
    ADD COLUMN account_number_enc VARCHAR(255);

ALTER TABLE loans
    ADD COLUMN loan_account_number_enc VARCHAR(255);

CREATE TABLE credit_report_accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    bank_name VARCHAR(160) NOT NULL,
    account_type VARCHAR(32) NOT NULL,
    account_number_enc VARCHAR(255),
    current_balance NUMERIC(19, 2) NOT NULL,
    credit_limit NUMERIC(19, 2),
    status VARCHAR(16) NOT NULL,
    report_date DATE,
    source_file_name VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_credit_report_accounts_type CHECK (account_type IN ('BANK', 'CREDIT_CARD', 'LOAN', 'OTHER')),
    CONSTRAINT ck_credit_report_accounts_status CHECK (status IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT ck_credit_report_accounts_balance CHECK (current_balance >= 0),
    CONSTRAINT ck_credit_report_accounts_limit CHECK (credit_limit IS NULL OR credit_limit >= 0)
);

CREATE INDEX idx_credit_report_accounts_user_id ON credit_report_accounts (user_id);
