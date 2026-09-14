package com.storemanager.sales.usecase;

/** One requested line of a sale, before the referenced product is validated and snapshotted. */
public record SaleLineItemRequest(Long productId, int quantity) {}
