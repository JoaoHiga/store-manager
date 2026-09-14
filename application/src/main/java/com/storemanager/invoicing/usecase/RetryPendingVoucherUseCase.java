package com.storemanager.invoicing.usecase;

import com.storemanager.invoicing.domain.Voucher;
import com.storemanager.invoicing.domain.VoucherStatus;
import com.storemanager.invoicing.domain.VoucherType;
import com.storemanager.invoicing.domain.port.InvoicingProvider;
import com.storemanager.invoicing.domain.port.IssuedVoucher;
import com.storemanager.invoicing.domain.port.VoucherRepositoryPort;
import com.storemanager.invoicing.domain.port.VoucherRequest;
import com.storemanager.sales.domain.Sale;
import com.storemanager.sales.domain.port.SaleRepositoryPort;
import com.storemanager.sales.usecase.SaleNotFoundException;
import java.time.Instant;

/**
 * Retries a {@code PENDING} or {@code NEEDS_ATTENTION} voucher against the {@link
 * InvoicingProvider} — used both by the scheduled retry job (PR4) and by a manual "retry now"
 * trigger, per spec.md's "Voucher Retry" requirement. A {@code PENDING} voucher that keeps failing
 * stays {@code PENDING} (attempt count incremented) until {@link #MAX_RETRY_ATTEMPTS} is reached,
 * at which point it escalates to {@code NEEDS_ATTENTION} and automatic retries stop; a manual
 * retry on a {@code NEEDS_ATTENTION} voucher reuses this same use case and can still succeed.
 */
public class RetryPendingVoucherUseCase {

    /** Bounded automatic-attempt cap before a {@code PENDING} voucher escalates (design.md). */
    static final int MAX_RETRY_ATTEMPTS = 10;

    private final VoucherRepositoryPort voucherRepository;
    private final SaleRepositoryPort saleRepository;
    private final InvoicingProvider invoicingProvider;

    public RetryPendingVoucherUseCase(
            VoucherRepositoryPort voucherRepository,
            SaleRepositoryPort saleRepository,
            InvoicingProvider invoicingProvider) {
        this.voucherRepository = voucherRepository;
        this.saleRepository = saleRepository;
        this.invoicingProvider = invoicingProvider;
    }

    public Voucher execute(Long voucherId) {
        Voucher voucher = voucherRepository.findById(voucherId).orElseThrow(() -> new VoucherNotFoundException(voucherId));
        if (voucher.getStatus() != VoucherStatus.PENDING && voucher.getStatus() != VoucherStatus.NEEDS_ATTENTION) {
            throw new IllegalStateException(
                    "Voucher " + voucherId + " is not retryable in status " + voucher.getStatus());
        }
        Sale sale =
                saleRepository.findById(voucher.getSaleId()).orElseThrow(
                        () -> new SaleNotFoundException(voucher.getSaleId()));
        VoucherRequest request = VoucherRequestMapper.toRequest(sale);
        Instant now = Instant.now();

        IssuedVoucher issued;
        try {
            issued = voucher.getType() == VoucherType.FACTURA
                    ? invoicingProvider.issueFactura(request)
                    : invoicingProvider.issueBoleta(request);
        } catch (RuntimeException ex) {
            return voucherRepository.save(handleFailure(voucher, ex.getMessage(), now));
        }

        if (issued.accepted()) {
            return voucherRepository.save(
                    voucher.accepted(
                            issued.voucherReference(), issued.pdfLink(), issued.xmlLink(), issued.cdrLink(),
                            issued.hash(), now));
        }
        return voucherRepository.save(handleFailure(voucher, issued.sunatDescription(), now));
    }

    private Voucher handleFailure(Voucher voucher, String reason, Instant now) {
        if (voucher.getStatus() == VoucherStatus.NEEDS_ATTENTION) {
            return voucher.needsAttention(reason, now);
        }
        boolean willExhaust = voucher.getAttemptCount() + 1 >= MAX_RETRY_ATTEMPTS;
        return willExhaust ? voucher.needsAttention(reason, now) : voucher.pending(reason, now);
    }
}
