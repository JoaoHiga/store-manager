-- Purchases: merchandise bought from a supplier, backed by the supplier's invoice (purchases feature,
-- odd/tasks/purchases.md). Recording one also increments product stock and recomputes the cost price;
-- that happens on `products` through a conditional UPDATE, not here.
-- GENERATED ALWAYS AS IDENTITY for PostgreSQL / H2 compatibility, as in V1.
-- supplier_name and product_name are snapshots taken when the purchase is recorded.

CREATE TABLE purchases (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    supplier_id    BIGINT NOT NULL,
    supplier_name  VARCHAR(160) NOT NULL,
    invoice_number VARCHAR(30) NOT NULL,
    purchase_date  DATE NOT NULL,
    total          NUMERIC(12, 2) NOT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT fk_purchases_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT uq_purchases_supplier_invoice UNIQUE (supplier_id, invoice_number),
    CONSTRAINT chk_purchases_total_non_negative CHECK (total >= 0)
);

CREATE INDEX idx_purchases_purchase_date ON purchases (purchase_date);

CREATE TABLE purchase_items (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    purchase_id  BIGINT NOT NULL,
    product_id   BIGINT NOT NULL,
    product_name VARCHAR(160) NOT NULL,
    quantity     INTEGER NOT NULL,
    unit_cost    NUMERIC(12, 2) NOT NULL,
    subtotal     NUMERIC(12, 2) NOT NULL,
    CONSTRAINT fk_purchase_items_purchase FOREIGN KEY (purchase_id) REFERENCES purchases (id),
    CONSTRAINT fk_purchase_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT uq_purchase_items_purchase_product UNIQUE (purchase_id, product_id),
    CONSTRAINT chk_purchase_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_purchase_items_unit_cost_non_negative CHECK (unit_cost >= 0),
    CONSTRAINT chk_purchase_items_subtotal_non_negative CHECK (subtotal >= 0)
);

CREATE INDEX idx_purchase_items_product_id ON purchase_items (product_id);
