-- Durable per-series correlativo counter for NubeFactInvoicingAdapter. Replaces the in-memory
-- counter flagged by RDD review-reliability (R3-nondurable-correlativo): an in-memory counter
-- resets to 1 on every restart and isn't shared across app instances, risking two different
-- vouchers being issued to SUNAT under the exact same series+correlativo — a real duplicate-legal-
-- document defect, not a cosmetic one.
CREATE TABLE voucher_correlativo_sequences (
    series VARCHAR(10) PRIMARY KEY,
    next_correlativo BIGINT NOT NULL DEFAULT 1
);

-- Pre-seeded with NubeFactInvoicingAdapter's fixed series constants. A row must already exist
-- before any concurrent request arrives: a `SELECT ... FOR UPDATE` locks nothing on a row that
-- doesn't exist yet, so two concurrent first-ever requests for the same brand-new series could
-- otherwise both decide to INSERT it and race on the primary key.
INSERT INTO voucher_correlativo_sequences (series, next_correlativo) VALUES
    ('B001', 1),
    ('F001', 1),
    ('BC01', 1),
    ('FC01', 1);
