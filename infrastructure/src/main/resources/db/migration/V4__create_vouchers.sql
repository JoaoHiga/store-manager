-- Vouchers (boletas/facturas) issued for sales through the configured OSE (NubeFacT). PR2 of
-- sales-invoicing: only the domain/use-case layer exists so far, no real OSE call yet (PR3) and
-- nothing wires this into RecordSaleUseCase yet (PR4) — see openspec/changes/sales-invoicing/design.md.
--
-- A separate table (not a status column merged onto `sales`) because a sale and its voucher have
-- different lifecycles: a voucher can be PENDING while its sale is already COMPLETED.
--
-- `series`/`correlativo` (the OSE's own document numbering) are intentionally NOT modeled yet:
-- `external_reference` is our own stable handle for a later checkStatus/voidVoucher call, and the
-- OSE-specific numbering scheme is deferred to PR3's NubeFactInvoicingAdapter (see design.md's
-- open questions).

CREATE TABLE vouchers (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sale_id                BIGINT NOT NULL,
    type                   VARCHAR(20) NOT NULL,
    status                 VARCHAR(20) NOT NULL,
    buyer_document_type    VARCHAR(10) NOT NULL,
    buyer_document_number  VARCHAR(20) NOT NULL,
    attempt_count          INTEGER NOT NULL DEFAULT 0,
    rejection_reason       VARCHAR(1000),
    external_reference     VARCHAR(100),
    pdf_link               VARCHAR(500),
    xml_link               VARCHAR(500),
    cdr_link               VARCHAR(500),
    hash                   VARCHAR(255),
    created_at             TIMESTAMP NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT fk_vouchers_sale FOREIGN KEY (sale_id) REFERENCES sales (id),
    CONSTRAINT uq_vouchers_sale_id UNIQUE (sale_id),
    CONSTRAINT chk_vouchers_attempt_count_non_negative CHECK (attempt_count >= 0)
);

CREATE INDEX idx_vouchers_status ON vouchers (status);
