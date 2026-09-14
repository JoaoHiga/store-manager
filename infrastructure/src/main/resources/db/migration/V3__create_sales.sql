-- Sales recorded against the product catalog. PR1 of sales-invoicing: no vouchers table yet
-- (V4, next PR) and `status` only ever holds 'COMPLETED' for now — see
-- openspec/changes/sales-invoicing/design.md.

CREATE TABLE sales (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    buyer_document_type   VARCHAR(10) NOT NULL,
    buyer_document_number VARCHAR(20) NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    total                 NUMERIC(12, 2) NOT NULL,
    created_at            TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_sales_total_non_negative CHECK (total >= 0)
);

CREATE INDEX idx_sales_status ON sales (status);

CREATE TABLE sale_items (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sale_id     BIGINT NOT NULL,
    product_id  BIGINT NOT NULL,
    description VARCHAR(1000),
    unit_price  NUMERIC(12, 2) NOT NULL,
    quantity    INTEGER NOT NULL,
    subtotal    NUMERIC(12, 2) NOT NULL,
    CONSTRAINT fk_sale_items_sale FOREIGN KEY (sale_id) REFERENCES sales (id),
    CONSTRAINT fk_sale_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_sale_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_sale_items_unit_price_positive CHECK (unit_price > 0),
    CONSTRAINT chk_sale_items_subtotal_non_negative CHECK (subtotal >= 0)
);

CREATE INDEX idx_sale_items_sale_id ON sale_items (sale_id);
CREATE INDEX idx_sale_items_product_id ON sale_items (product_id);
