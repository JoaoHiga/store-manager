-- buyer-identification-hardening: a sale under S/ 700 ("boleta simple") may now be recorded with
-- no buyer document at all. Both `sales` and `vouchers` independently enforced NOT NULL on these
-- two columns (V3, V4) — Sale's and Voucher's own constructors now allow a null buyer reference
-- (validated by BuyerDocumentValidator), so both tables need the same relaxation. Safe/reversible:
-- no existing row has NULL in either column yet (every sale so far had a required buyer
-- reference), see design.md's Migration/Rollout.

ALTER TABLE sales ALTER COLUMN buyer_document_type DROP NOT NULL;
ALTER TABLE sales ALTER COLUMN buyer_document_number DROP NOT NULL;

ALTER TABLE vouchers ALTER COLUMN buyer_document_type DROP NOT NULL;
ALTER TABLE vouchers ALTER COLUMN buyer_document_number DROP NOT NULL;
