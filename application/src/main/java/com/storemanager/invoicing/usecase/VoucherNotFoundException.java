package com.storemanager.invoicing.usecase;

public class VoucherNotFoundException extends RuntimeException {

    public VoucherNotFoundException(Long voucherId) {
        super("Voucher with id " + voucherId + " does not exist");
    }

    private VoucherNotFoundException(String message) {
        super(message);
    }

    public static VoucherNotFoundException forSale(Long saleId) {
        return new VoucherNotFoundException("Sale with id " + saleId + " has no voucher");
    }
}
