package com.storemanager.invoicing.domain.port;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything the {@link InvoicingProvider} needs to issue a boleta or factura. Our own shape (see
 * design.md) — the adapter implementing this port maps it onto the OSE's literal request fields;
 * nothing OSE-specific belongs here.
 */
public record VoucherRequest(
        String buyerDocumentType, String buyerDocumentNumber, List<VoucherLineItem> lineItems, BigDecimal total) {}
