-- V5's seeded series (B001/F001/BC01/FC01) were an unconfirmed guess at NubeFacT's convention.
-- Confirmed against the real account's panel (Configuracion > Series): factura is FFF1, boleta is
-- BBB1, and a credit/debit note shares its parent document's series instead of using a dedicated
-- one — see NubeFactInvoicingAdapter's updated class javadoc. Only FFF1/BBB1 are needed now.
DELETE FROM voucher_correlativo_sequences WHERE series IN ('B001', 'F001', 'BC01', 'FC01');

INSERT INTO voucher_correlativo_sequences (series, next_correlativo) VALUES
    ('BBB1', 1),
    ('FFF1', 1);
