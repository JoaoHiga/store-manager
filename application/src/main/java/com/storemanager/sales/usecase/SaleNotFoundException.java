package com.storemanager.sales.usecase;

public class SaleNotFoundException extends RuntimeException {

    public SaleNotFoundException(Long saleId) {
        super("Sale with id " + saleId + " does not exist");
    }
}
