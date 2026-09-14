package com.storemanager.invoicing.usecase;

import com.storemanager.invoicing.domain.VoucherStatus;

public class VoucherNotVoidableException extends RuntimeException {

    public VoucherNotVoidableException(VoucherStatus status) {
        super("Only an ACCEPTED voucher can be voided; current status is " + status);
    }
}
