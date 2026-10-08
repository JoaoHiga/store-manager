package com.storemanager.purchasing.usecase;

/** Thrown when a Supplier is created with a RUC that already belongs to another supplier. */
public class DuplicateSupplierRucException extends RuntimeException {

    public DuplicateSupplierRucException(String ruc) {
        super("A supplier with RUC " + ruc + " already exists");
    }
}
