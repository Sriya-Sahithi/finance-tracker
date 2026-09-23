CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    account_id BIGINT NOT NULL REFERENCES accounts (id),
    transfer_account_id BIGINT REFERENCES accounts (id),
    category_id BIGINT REFERENCES categories (id),
    type VARCHAR(16) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    transaction_date DATE NOT NULL,
    description VARCHAR(255),
    notes VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_transactions_type CHECK (type IN ('INCOME', 'EXPENSE', 'TRANSFER', 'LOAN_PAYMENT')),
    CONSTRAINT ck_transactions_amount CHECK (amount > 0),
    CONSTRAINT ck_transactions_transfer CHECK (
        (type = 'TRANSFER' AND transfer_account_id IS NOT NULL AND transfer_account_id <> account_id)
        OR (type <> 'TRANSFER' AND transfer_account_id IS NULL)
    ),
    CONSTRAINT ck_transactions_category CHECK (
        (type IN ('INCOME', 'EXPENSE') AND category_id IS NOT NULL)
        OR (type IN ('TRANSFER', 'LOAN_PAYMENT'))
    )
);

CREATE INDEX idx_transactions_user_id ON transactions (user_id);
CREATE INDEX idx_transactions_transaction_date ON transactions (transaction_date);
CREATE INDEX idx_transactions_category_id ON transactions (category_id);
CREATE INDEX idx_transactions_account_id ON transactions (account_id);
CREATE INDEX idx_transactions_user_date ON transactions (user_id, transaction_date);
CREATE INDEX idx_transactions_transfer_account_id ON transactions (transfer_account_id);
