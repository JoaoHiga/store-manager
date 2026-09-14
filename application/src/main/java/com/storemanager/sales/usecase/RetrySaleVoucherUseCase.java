package com.storemanager.sales.usecase;

import com.storemanager.invoicing.domain.Voucher;
import com.storemanager.invoicing.domain.port.VoucherRepositoryPort;
import com.storemanager.invoicing.usecase.RetryPendingVoucherUseCase;
import com.storemanager.invoicing.usecase.VoucherNotFoundException;

/**
 * Retries a sale's voucher on demand, per spec.md's "Voucher Retry" requirement and design.md's
 * manual "retry now" trigger (used by {@code POST /api/sales/{id}/retry-voucher}, PR4).
 * {@code RetryPendingVoucherUseCase} (PR2) takes a voucher id, not a sale id — shared as-is with
 * the scheduled {@code RetryPendingVouchersJob}, whose {@code VoucherRepositoryPort#findAllPending}
 * query already returns voucher ids directly. This use case resolves the sale's voucher via {@code
 * VoucherRepositoryPort#findBySaleId} first and then delegates, so {@code RetryPendingVoucherUseCase}'s
 * signature stays untouched and {@code SaleController} never reaches into {@code
 * invoicing.domain.port.VoucherRepositoryPort} directly (a controller belongs to {@code sales}).
 */
public class RetrySaleVoucherUseCase {

    private final VoucherRepositoryPort voucherRepository;
    private final RetryPendingVoucherUseCase retryPendingVoucherUseCase;

    public RetrySaleVoucherUseCase(
            VoucherRepositoryPort voucherRepository, RetryPendingVoucherUseCase retryPendingVoucherUseCase) {
        this.voucherRepository = voucherRepository;
        this.retryPendingVoucherUseCase = retryPendingVoucherUseCase;
    }

    public Voucher execute(Long saleId) {
        Voucher voucher =
                voucherRepository.findBySaleId(saleId).orElseThrow(() -> VoucherNotFoundException.forSale(saleId));
        return retryPendingVoucherUseCase.execute(voucher.getId());
    }
}
