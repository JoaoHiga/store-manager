package com.storemanager.sales.usecase;

/**
 * Thrown by {@link CancelSaleUseCase} when the voucher was already confirmed voided by the OSE
 * (an irreversible external call) but persisting the sale as {@code CANCELLED} afterward failed.
 * Distinct from a generic propagated exception so this specific, already-inconsistent state is
 * identifiable — per RDD review-reliability finding R3-cancel-not-atomic — for manual
 * reconciliation (the voucher record is the source of truth; the sale just needs to catch up).
 */
public class SaleCancellationInconsistentException extends RuntimeException {

    public SaleCancellationInconsistentException(Long saleId, Throwable cause) {
        super(
                "Voucher for sale "
                        + saleId
                        + " was already voided by the invoicing provider, but the sale record could not be"
                        + " updated to CANCELLED — needs manual reconciliation",
                cause);
    }
}
