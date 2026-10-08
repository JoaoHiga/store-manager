package com.storemanager.purchasing.usecase;

import java.math.BigDecimal;

/** One requested line of a purchase, before the referenced product is validated and snapshotted. */
public record PurchaseLineItemRequest(Long productId, int quantity, BigDecimal unitCost) {}
