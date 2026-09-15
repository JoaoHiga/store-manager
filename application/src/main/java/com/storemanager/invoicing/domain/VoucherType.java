package com.storemanager.invoicing.domain;

/**
 * The kind of SUNAT electronic document a {@link Voucher} represents. Chosen explicitly by the
 * caller at sale-recording time (see design.md and spec.md, "Voucher Type Determination"), not
 * derived from the buyer's document type: {@code FACTURA} requires a RUC buyer; {@code BOLETA} is
 * always allowed, including for a RUC buyer.
 */
public enum VoucherType {
    BOLETA,
    FACTURA
}
