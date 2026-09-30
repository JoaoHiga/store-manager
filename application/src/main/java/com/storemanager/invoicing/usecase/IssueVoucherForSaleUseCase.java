package com.storemanager.invoicing.usecase;

import com.storemanager.invoicing.domain.Voucher;
import com.storemanager.invoicing.domain.VoucherType;
import com.storemanager.invoicing.domain.port.InvoicingProvider;
import com.storemanager.invoicing.domain.port.IssuedVoucher;
import com.storemanager.invoicing.domain.port.VoucherRepositoryPort;
import com.storemanager.invoicing.domain.port.VoucherRequest;
import com.storemanager.sales.domain.Sale;
import java.time.Instant;

/**
 * Issues a voucher for a just-completed sale, per spec.md's "Voucher Type Determination" and
 * "Voucher Issuance via OSE" requirements. The voucher type is an explicit caller choice (see
 * design.md, "explicit-voucher-type-selection") rather than derived from the buyer's document
 * type: the one enforced constraint is that {@code FACTURA} requires a RUC buyer — {@code BOLETA}
 * is always allowed, regardless of buyer document type (including RUC). The sale itself always
 * completes regardless of the issuance outcome: any provider rejection, timeout, or thrown
 * exception is caught and persisted as a {@code PENDING} voucher (retaining the rejection reason)
 * instead of propagating — never blocking or rolling back the sale (see design.md's
 * failure/retry path).
 */
public class IssueVoucherForSaleUseCase {

    private final VoucherRepositoryPort voucherRepository;
    private final InvoicingProvider invoicingProvider;

    public IssueVoucherForSaleUseCase(VoucherRepositoryPort voucherRepository, InvoicingProvider invoicingProvider) {
        this.voucherRepository = voucherRepository;
        this.invoicingProvider = invoicingProvider;
    }

    /** Registers the voucher and attempts issuance right away: {@link #registerPending} then {@link #issue}. */
    public Voucher execute(Sale sale, VoucherType requestedType) {
        return issue(registerPending(sale, requestedType), sale);
    }

    /**
     * Persists a {@code PENDING} voucher (zero attempts) for the sale, without calling the provider. Meant to
     * run inside the same transaction that records the sale, so a sale never exists without its voucher: if
     * the process dies before {@link #issue} runs, {@code RetryPendingVouchersJob} still finds and issues it.
     */
    public Voucher registerPending(Sale sale, VoucherType requestedType) {
        if (requestedType == VoucherType.FACTURA && !"RUC".equalsIgnoreCase(sale.getBuyerDocumentType())) {
            throw new IllegalArgumentException("Factura requires a RUC buyer document");
        }
        return voucherRepository.save(
                Voucher.createNew(
                        sale.getId(), requestedType, sale.getBuyerDocumentType(), sale.getBuyerDocumentNumber(),
                        Instant.now()));
    }

    /**
     * Attempts issuance of a voucher saved by {@link #registerPending}. Calls the provider, so it must run
     * outside any database transaction. Never throws for a provider failure: the voucher stays {@code PENDING}
     * with the reason recorded.
     */
    public Voucher issue(Voucher pendingVoucher, Sale sale) {
        Instant now = Instant.now();
        VoucherRequest request = VoucherRequestMapper.toRequest(sale);

        IssuedVoucher issued;
        try {
            issued = pendingVoucher.getType() == VoucherType.FACTURA
                    ? invoicingProvider.issueFactura(request)
                    : invoicingProvider.issueBoleta(request);
        } catch (RuntimeException ex) {
            return voucherRepository.save(pendingVoucher.pending(ex.getMessage(), now));
        }

        if (issued.accepted()) {
            return voucherRepository.save(
                    pendingVoucher.accepted(
                            issued.voucherReference(), issued.pdfLink(), issued.xmlLink(), issued.cdrLink(),
                            issued.hash(), now));
        }
        return voucherRepository.save(pendingVoucher.pending(issued.sunatDescription(), now));
    }
}
