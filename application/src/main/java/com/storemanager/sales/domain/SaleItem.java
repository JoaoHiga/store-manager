package com.storemanager.sales.domain;

import java.math.BigDecimal;

/**
 * A single line of a {@link Sale}. {@code description} (the product's name at the time of sale) and
 * {@code unitPrice} are a snapshot of the referenced product taken at sale time (see design.md,
 * Decision: Sale line-item pricing) — they never change even if the product's own {@code name}/{@code
 * salePrice} change later.
 * {@code subtotal} is derived from {@code unitPrice * quantity} and enforced here, not passed in,
 * so it can never drift from the two values it's computed from.
 */
public final class SaleItem {

    private final Long id;
    private final Long productId;
    private final String description;
    private final BigDecimal unitPrice;
    private final int quantity;
    private final BigDecimal subtotal;

    private SaleItem(Long id, Long productId, String description, BigDecimal unitPrice, int quantity) {
        if (productId == null) {
            throw new IllegalArgumentException("SaleItem productId is required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("SaleItem quantity must be greater than zero");
        }
        if (unitPrice == null || unitPrice.signum() <= 0) {
            throw new IllegalArgumentException("SaleItem unitPrice must be greater than zero");
        }
        this.id = id;
        this.productId = productId;
        this.description = description;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public static SaleItem createNew(Long productId, String description, BigDecimal unitPrice, int quantity) {
        return new SaleItem(null, productId, description, unitPrice, quantity);
    }

    public static SaleItem reconstitute(
            Long id, Long productId, String description, BigDecimal unitPrice, int quantity) {
        return new SaleItem(id, productId, description, unitPrice, quantity);
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }
}
