package com.storemanager.purchasing.usecase;

/** Purchasing's own copy, like {@code sales}: each bounded context owns its exceptions. */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(Long productId) {
        super("Product with id " + productId + " does not exist");
    }
}
