package com.storemanager.purchasing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A single line of a {@link Purchase}: how many units of a product came in and what each one cost.
 * {@code productName} is a snapshot taken when the purchase is recorded, so the line keeps the name it
 * was bought under even if the product is renamed later. {@code subtotal} is derived from
 * {@code unitCost * quantity}, never passed in.
 */
public final class PurchaseItem {

    private final Long id;
    private final Long productId;
    private final String productName;
    private final int quantity;
    private final BigDecimal unitCost;
    private final BigDecimal subtotal;

    private PurchaseItem(Long id, Long productId, String productName, int quantity, BigDecimal unitCost) {
        if (productId == null) {
            throw new IllegalArgumentException("PurchaseItem productId is required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Purchase line quantity must be greater than zero");
        }
        if (unitCost == null || unitCost.signum() < 0) {
            throw new IllegalArgumentException("Purchase line unit cost must be zero or greater");
        }
        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitCost = toMoney(unitCost);
        this.subtotal = this.unitCost.multiply(BigDecimal.valueOf(quantity));
    }

    /** Scale 2 without rounding: a cost with more decimals is rejected rather than silently changed. */
    private static BigDecimal toMoney(BigDecimal value) {
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Purchase line unit cost must have at most 2 decimals");
        }
    }

    public static PurchaseItem createNew(Long productId, String productName, int quantity, BigDecimal unitCost) {
        return new PurchaseItem(null, productId, productName, quantity, unitCost);
    }

    public static PurchaseItem reconstitute(
            Long id, Long productId, String productName, int quantity, BigDecimal unitCost) {
        return new PurchaseItem(id, productId, productName, quantity, unitCost);
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }
}
