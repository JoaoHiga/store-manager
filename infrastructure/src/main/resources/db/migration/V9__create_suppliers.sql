-- Suppliers the store buys merchandise from (purchases feature, odd/tasks/purchases.md).
-- GENERATED ALWAYS AS IDENTITY for PostgreSQL / H2 compatibility, as in V1.
-- The RUC check digit is validated by the application; the database only guards uniqueness.
CREATE TABLE suppliers (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(160) NOT NULL,
    ruc        VARCHAR(11) NOT NULL,
    phone      VARCHAR(30),
    email      VARCHAR(120),
    address    VARCHAR(300),
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_suppliers_ruc UNIQUE (ruc)
);
