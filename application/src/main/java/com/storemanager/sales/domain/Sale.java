package com.storemanager.sales.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A recorded sale against the product catalog: one or more {@link SaleItem}s plus a buyer
 * reference. {@code total} is derived from the sum of the items' subtotals and enforced here, not
 * passed in, so it can never disagree with its own line items. This class only enforces its own
 * structural invariants — that a referenced product exists, is active, and has enough stock is
 * validated by {@link com.storemanager.sales.usecase.RecordSaleUseCase} via
 * {@code ProductRepositoryPort}, not here.
 */
public final class Sale {

    private final Long id;
    private final List<SaleItem> items;
    private final String buyerDocumentType;
    private final String buyerDocumentNumber;
    private final BigDecimal total;
    private final SaleStatus status;
    private final Instant createdAt;

    private Sale(
            Long id,
            List<SaleItem> items,
            String buyerDocumentType,
            String buyerDocumentNumber,
            SaleStatus status,
            Instant createdAt) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Sale must have at least one line item");
        }
        if (buyerDocumentType == null || buyerDocumentType.isBlank()) {
            throw new IllegalArgumentException("Sale buyer documentType is required");
        }
        if (buyerDocumentNumber == null || buyerDocumentNumber.isBlank()) {
            throw new IllegalArgumentException("Sale buyer documentNumber is required");
        }
        this.id = id;
        this.items = List.copyOf(items);
        this.buyerDocumentType = buyerDocumentType;
        this.buyerDocumentNumber = buyerDocumentNumber;
        this.status = status;
        this.createdAt = createdAt;
        this.total = this.items.stream().map(SaleItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static Sale createNew(
            List<SaleItem> items, String buyerDocumentType, String buyerDocumentNumber, Instant now) {
        return new Sale(null, items, buyerDocumentType, buyerDocumentNumber, SaleStatus.COMPLETED, now);
    }

    public static Sale reconstitute(
            Long id,
            List<SaleItem> items,
            String buyerDocumentType,
            String buyerDocumentNumber,
            SaleStatus status,
            Instant createdAt) {
        return new Sale(id, items, buyerDocumentType, buyerDocumentNumber, status, createdAt);
    }

    public Long getId() {
        return id;
    }

    public List<SaleItem> getItems() {
        return items;
    }

    public String getBuyerDocumentType() {
        return buyerDocumentType;
    }

    public String getBuyerDocumentNumber() {
        return buyerDocumentNumber;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public SaleStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Marks this sale {@code CANCELLED} once its voucher has been voided (see {@code
     * CancelSaleUseCase}, PR4 of sales-invoicing, and design.md's void path: "Voucher -&gt; VOIDED,
     * Sale -&gt; CANCELLED"). A soft-state transition — this never deletes a row. Mirrors {@code
     * Product}'s {@code deactivated()}/{@code activated()} transition-method shape; {@code now} is
     * accepted for that same symmetry even though {@code Sale} does not (yet) track an {@code
     * updatedAt} column the way {@code Product} does.
     */
    public Sale cancelled(Instant now) {
        return new Sale(id, items, buyerDocumentType, buyerDocumentNumber, SaleStatus.CANCELLED, createdAt);
    }
}
