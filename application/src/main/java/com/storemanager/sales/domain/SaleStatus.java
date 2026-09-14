package com.storemanager.sales.domain;

/**
 * A sale's lifecycle state. Every recorded sale starts {@code COMPLETED} (see {@link
 * com.storemanager.sales.usecase.RecordSaleUseCase}); {@code CANCELLED} is reached only via {@link
 * Sale#cancelled(java.time.Instant)}, once its voucher has been voided (see {@code
 * CancelSaleUseCase}, PR4 of sales-invoicing, and design.md's void path). A sale is never deleted,
 * only transitioned to {@code CANCELLED} — a soft state.
 */
public enum SaleStatus {
    COMPLETED,
    CANCELLED
}
