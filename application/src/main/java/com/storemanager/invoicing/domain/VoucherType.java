package com.storemanager.invoicing.domain;

/**
 * The kind of SUNAT electronic document a {@link Voucher} represents. Determined from the sale's
 * buyer document type: a RUC buyer (a company) gets a {@code FACTURA}; any other supported
 * document type (e.g. DNI) gets a {@code BOLETA} — see design.md and spec.md, "Voucher Type
 * Determination".
 */
public enum VoucherType {
    BOLETA,
    FACTURA
}
