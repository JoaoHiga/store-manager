package com.storemanager.purchasing.usecase;

public class SupplierNotFoundException extends RuntimeException {

    public SupplierNotFoundException(Long supplierId) {
        super("Supplier with id " + supplierId + " does not exist");
    }
}
