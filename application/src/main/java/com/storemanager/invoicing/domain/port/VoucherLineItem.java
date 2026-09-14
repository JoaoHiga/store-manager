package com.storemanager.invoicing.domain.port;

import java.math.BigDecimal;

/**
 * One line item as sent to the {@link InvoicingProvider}. Mirrors {@code
 * com.storemanager.sales.domain.SaleItem}'s already-snapshotted {@code description}/{@code
 * unitPrice} — this is our own shape, not NubeFacT's literal {@code items[]} field names (see
 * design.md); the adapter that maps to those fields is PR3's job.
 */
public record VoucherLineItem(String description, BigDecimal unitPrice, int quantity, BigDecimal subtotal) {}
