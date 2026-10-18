package com.storemanager.purchasing.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Merchandise bought from a supplier, backed by the supplier's invoice: one or more {@link PurchaseItem}s.
 * {@code total} is derived from the items' subtotals, never passed in. {@code supplierName} is a snapshot
 * taken when the purchase is recorded, like each item's product name.
 *
 * <p>This class only enforces its own invariants. That the supplier and products exist and are active,
 * and that the supplier's invoice number is not already registered, is checked by
 * {@link com.storemanager.purchasing.usecase.RecordPurchaseUseCase} through the repository ports.
 */
public final class Purchase {

    public static final int INVOICE_NUMBER_MAX_LENGTH = 30;

    private final Long id;
    private final Long supplierId;
    private final String supplierName;
    private final String invoiceNumber;
    private final LocalDate purchaseDate;
    private final List<PurchaseItem> items;
    private final BigDecimal total;
    private final Instant createdAt;

    private Purchase(
            Long id,
            Long supplierId,
            String supplierName,
            String invoiceNumber,
            LocalDate purchaseDate,
            List<PurchaseItem> items,
            Instant createdAt) {
        if (supplierId == null) {
            throw new IllegalArgumentException("Purchase supplierId is required");
        }
        if (invoiceNumber == null || invoiceNumber.isBlank()) {
            throw new IllegalArgumentException("Purchase invoice number is required");
        }
        String strippedInvoiceNumber = invoiceNumber.strip();
        if (strippedInvoiceNumber.length() > INVOICE_NUMBER_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Purchase invoice number must be at most " + INVOICE_NUMBER_MAX_LENGTH + " characters");
        }
        if (purchaseDate == null) {
            throw new IllegalArgumentException("Purchase date is required");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Purchase must have at least one line");
        }
        Set<Long> productIds = new HashSet<>();
        for (PurchaseItem item : items) {
            if (!productIds.add(item.getProductId())) {
                throw new IllegalArgumentException(
                        "Product with id " + item.getProductId() + " appears more than once in the purchase");
            }
        }
        this.id = id;
        this.supplierId = supplierId;
        this.supplierName = supplierName;
        this.invoiceNumber = strippedInvoiceNumber;
        this.purchaseDate = purchaseDate;
        this.items = List.copyOf(items);
        this.total = this.items.stream().map(PurchaseItem::getSubtotal).reduce(new BigDecimal("0.00"), BigDecimal::add);
        this.createdAt = createdAt;
    }

    /**
     * {@code today} is the store's current date: a purchase cannot be dated in the future. That is a
     * create-time rule only; {@link #reconstitute} does not re-check it.
     */
    public static Purchase createNew(
            Long supplierId,
            String supplierName,
            String invoiceNumber,
            LocalDate purchaseDate,
            List<PurchaseItem> items,
            LocalDate today,
            Instant now) {
        if (purchaseDate != null && purchaseDate.isAfter(today)) {
            throw new IllegalArgumentException("Purchase date must not be in the future");
        }
        return new Purchase(null, supplierId, supplierName, invoiceNumber, purchaseDate, items, now);
    }

    public static Purchase reconstitute(
            Long id,
            Long supplierId,
            String supplierName,
            String invoiceNumber,
            LocalDate purchaseDate,
            List<PurchaseItem> items,
            Instant createdAt) {
        return new Purchase(id, supplierId, supplierName, invoiceNumber, purchaseDate, items, createdAt);
    }

    public Long getId() {
        return id;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public List<PurchaseItem> getItems() {
        return items;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
