CREATE TABLE loan_payments (
    id BIGSERIAL PRIMARY KEY,
    loan_id BIGINT NOT NULL REFERENCES loans (id),
    payment_date DATE NOT NULL,
    total_amount NUMERIC(19, 2) NOT NULL,
    principal_amount NUMERIC(19, 2) NOT NULL,
    interest_amount NUMERIC(19, 2) NOT NULL,
    extra_principal_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    remaining_principal NUMERIC(19, 2) NOT NULL,
    notes VARCHAR(1000),
    transaction_id BIGINT REFERENCES transactions (id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_loan_payments_total CHECK (total_amount > 0),
    CONSTRAINT ck_loan_payments_parts CHECK (
        principal_amount >= 0 AND interest_amount >= 0 AND extra_principal_amount >= 0
    ),
    CONSTRAINT ck_loan_payments_remaining CHECK (remaining_principal >= 0)
);

CREATE INDEX idx_loan_payments_loan_id ON loan_payments (loan_id);
CREATE INDEX idx_loan_payments_transaction_id ON loan_payments (transaction_id);
