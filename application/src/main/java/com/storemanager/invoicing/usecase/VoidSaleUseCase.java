package com.storemanager.invoicing.usecase;

import com.storemanager.invoicing.domain.Voucher;
import com.storemanager.invoicing.domain.VoucherStatus;
import com.storemanager.invoicing.domain.port.InvoicingProvider;
import com.storemanager.invoicing.domain.port.IssuedVoucher;
import com.storemanager.invoicing.domain.port.VoucherRepositoryPort;
import java.time.Instant;

/**
 * Voids a sale's voucher by issuing a comunicación de baja through the {@link InvoicingProvider},
 * per spec.md's "Voucher Void" requirement. Only an {@code ACCEPTED} voucher can be voided; any
 * other status is rejected with {@link VoucherNotVoidableException}.
 *
 * <p>Known PR4 dependency: this use case only transitions the {@code Voucher} to {@code VOIDED}.
 * Marking the {@code Sale} itself {@code CANCELLED} is out of scope here — that wiring happens
 * once {@code RecordSaleUseCase}/{@code SaleController} are connected to invoicing (see
 * design.md's void path and tasks.md Phase 4).
 */
public class VoidSaleUseCase {

    private final VoucherRepositoryPort voucherRepository;
    private final InvoicingProvider invoicingProvider;

    public VoidSaleUseCase(VoucherRepositoryPort voucherRepository, InvoicingProvider invoicingProvider) {
        this.voucherRepository = voucherRepository;
        this.invoicingProvider = invoicingProvider;
    }

    public Voucher execute(Long saleId, String reason) {
        Voucher voucher =
                voucherRepository.findBySaleId(saleId).orElseThrow(() -> VoucherNotFoundException.forSale(saleId));
        if (voucher.getStatus() != VoucherStatus.ACCEPTED) {
            throw new VoucherNotVoidableException(voucher.getStatus());
        }
        IssuedVoucher result = invoicingProvider.voidVoucher(voucher.getExternalReference(), reason);
        if (!result.accepted()) {
            throw new VoidRejectedException(result.sunatDescription());
        }
        return voucherRepository.save(voucher.voided(Instant.now()));
    }
}
