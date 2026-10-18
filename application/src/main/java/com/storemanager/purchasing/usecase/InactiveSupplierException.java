package com.storemanager.purchasing.usecase;

public class InactiveSupplierException extends RuntimeException {

    public InactiveSupplierException(Long supplierId) {
        super("Supplier with id " + supplierId + " is not active");
    }
}
