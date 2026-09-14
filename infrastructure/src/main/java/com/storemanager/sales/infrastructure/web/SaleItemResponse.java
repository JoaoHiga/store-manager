package com.storemanager.sales.infrastructure.web;

import com.storemanager.sales.domain.SaleItem;
import java.math.BigDecimal;

public record SaleItemResponse(
        Long id, Long productId, String description, BigDecimal unitPrice, int quantity, BigDecimal subtotal) {

    public static SaleItemResponse from(SaleItem item) {
        return new SaleItemResponse(
                item.getId(), item.getProductId(), item.getDescription(), item.getUnitPrice(), item.getQuantity(),
                item.getSubtotal());
    }
}
