package com.storemanager.sales.usecase;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(Long productId, int requestedQuantity, int availableQuantity) {
        super(
                "Product with id " + productId + " has only " + availableQuantity
                        + " units in stock, requested " + requestedQuantity);
    }
}
