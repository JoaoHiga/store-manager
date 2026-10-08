package com.storemanager.purchasing.usecase;

public class PurchaseNotFoundException extends RuntimeException {

    public PurchaseNotFoundException(Long purchaseId) {
        super("Purchase with id " + purchaseId + " does not exist");
    }
}
