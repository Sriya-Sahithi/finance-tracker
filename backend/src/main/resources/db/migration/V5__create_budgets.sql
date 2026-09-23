CREATE TABLE budgets (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    category_id BIGINT NOT NULL REFERENCES categories (id),
    year INTEGER NOT NULL,
    month INTEGER NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_budgets_month CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT ck_budgets_year CHECK (year BETWEEN 2000 AND 2100),
    CONSTRAINT ck_budgets_amount CHECK (amount > 0),
    CONSTRAINT uq_budgets_user_category_period UNIQUE (user_id, category_id, year, month)
);

CREATE INDEX idx_budgets_user_id ON budgets (user_id);
CREATE INDEX idx_budgets_category_id ON budgets (category_id);
CREATE INDEX idx_budgets_user_period ON budgets (user_id, year, month);
