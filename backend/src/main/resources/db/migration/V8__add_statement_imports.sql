ALTER TABLE accounts
    ADD COLUMN account_number VARCHAR(34);

ALTER TABLE transactions
    ADD COLUMN import_session_id UUID,
    ADD COLUMN import_row_fingerprint VARCHAR(64);

CREATE TABLE statement_import_sessions (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    file_name VARCHAR(255) NOT NULL,
    detected_account_name VARCHAR(255),
    detected_account_number VARCHAR(34),
    preview_rows_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ
);

CREATE INDEX idx_statement_import_sessions_user_id ON statement_import_sessions (user_id, created_at DESC);
CREATE UNIQUE INDEX uq_transactions_user_import_row_fingerprint
    ON transactions (user_id, import_row_fingerprint)
    WHERE import_row_fingerprint IS NOT NULL;
