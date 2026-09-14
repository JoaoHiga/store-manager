package com.storemanager.sales.usecase;

public class InactiveProductException extends RuntimeException {

    public InactiveProductException(Long productId) {
        super("Product with id " + productId + " is not active");
    }
}
