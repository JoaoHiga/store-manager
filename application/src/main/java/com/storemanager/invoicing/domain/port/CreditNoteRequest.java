package com.storemanager.invoicing.domain.port;

import java.math.BigDecimal;
import java.util.List;

/**
 * A request to issue a credit note against a previously-issued voucher. Not exercised by any PR2
 * use case yet — reserved for a future credit-note flow (see design.md's {@link
 * InvoicingProvider} contract) — but the port and this shape are defined now so PR3's adapter can
 * implement the full contract in one pass.
 */
public record CreditNoteRequest(
        String originalVoucherReference, String reason, List<VoucherLineItem> lineItems, BigDecimal total) {}
