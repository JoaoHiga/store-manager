-- Product catalog foundation: categories and products.
-- See openspec/changes/inventory-foundation/design.md for the full rationale.

-- GENERATED ALWAYS AS IDENTITY (SQL-standard) instead of BIGSERIAL: works
-- identically on PostgreSQL 10+ and on H2's PostgreSQL-compatibility mode,
-- which the test profile uses for @DataJpaTest integration tests.
CREATE TABLE categories (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_categories_name UNIQUE (name)
);

-- Dedicated sequence for SKU numbering (PRD-000123). NOT the primary key
-- sequence: an id-derived SKU can't satisfy the NOT NULL constraint below,
-- because the id is only known after INSERT while sku must be present IN the
-- INSERT. A separate sequence is just as race-free and zero-maintenance as
-- reusing the primary key would have been, without that ordering problem.
CREATE SEQUENCE product_sku_seq START WITH 1;

CREATE TABLE products (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku            VARCHAR(20) NOT NULL,
    name           VARCHAR(160) NOT NULL,
    description    VARCHAR(1000),
    category_id    BIGINT NOT NULL,
    brand          VARCHAR(80),
    cost_price     NUMERIC(12, 2),
    sale_price     NUMERIC(12, 2) NOT NULL,
    stock_quantity INTEGER NOT NULL DEFAULT 0,
    active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_products_sku UNIQUE (sku),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT chk_products_stock_non_negative CHECK (stock_quantity >= 0),
    CONSTRAINT chk_products_sale_price_positive CHECK (sale_price > 0)
);

CREATE INDEX idx_products_category_id ON products (category_id);
CREATE INDEX idx_products_active ON products (active);
