package com.storemanager.purchasing.infrastructure.web;

import com.storemanager.purchasing.domain.PurchaseItem;
import java.math.BigDecimal;

public record PurchaseItemResponse(
        Long id, Long productId, String productName, int quantity, BigDecimal unitCost, BigDecimal subtotal) {

    public static PurchaseItemResponse from(PurchaseItem item) {
        return new PurchaseItemResponse(
                item.getId(), item.getProductId(), item.getProductName(), item.getQuantity(), item.getUnitCost(),
                item.getSubtotal());
    }
}
