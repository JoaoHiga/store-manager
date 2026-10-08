package com.storemanager.purchasing.usecase;

/** Purchasing's own copy, like {@code sales}: each bounded context owns its exceptions. */
public class InactiveProductException extends RuntimeException {

    public InactiveProductException(Long productId) {
        super("Product with id " + productId + " is not active");
    }
}
