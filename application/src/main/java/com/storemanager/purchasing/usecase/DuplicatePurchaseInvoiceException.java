package com.storemanager.purchasing.usecase;

/** Thrown when a supplier's invoice number is already registered in another purchase. */
public class DuplicatePurchaseInvoiceException extends RuntimeException {

    public DuplicatePurchaseInvoiceException(Long supplierId, String invoiceNumber) {
        super("A purchase with invoice number " + invoiceNumber + " already exists for supplier " + supplierId);
    }
}
