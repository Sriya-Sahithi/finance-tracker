CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    name VARCHAR(120) NOT NULL,
    type VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_categories_type CHECK (type IN ('INCOME', 'EXPENSE'))
);

CREATE UNIQUE INDEX uq_categories_user_type_name ON categories (user_id, type, lower(name));
CREATE INDEX idx_categories_user_id ON categories (user_id);
