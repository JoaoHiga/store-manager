-- Closes a gap found by RDD review: costPrice had no lower bound anywhere.
-- See openspec/changes/product-catalog-hardening/design.md.
ALTER TABLE products
    ADD CONSTRAINT chk_products_cost_price_non_negative CHECK (cost_price IS NULL OR cost_price >= 0);
