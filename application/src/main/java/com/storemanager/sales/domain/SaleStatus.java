package com.storemanager.sales.domain;

/**
 * A sale's lifecycle state. Only {@code COMPLETED} is produced in this slice (PR1 of
 * sales-invoicing): every recorded sale completes immediately, nothing here calls invoicing yet.
 * {@code CANCELLED} is reserved for the void flow added once invoicing lands (see proposal.md,
 * "Basic void flow") and is intentionally not added until that behavior exists.
 */
public enum SaleStatus {
    COMPLETED
}
