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

    public Voucher execute(Sale sale, VoucherType requestedType) {
        if (requestedType == VoucherType.FACTURA && !"RUC".equalsIgnoreCase(sale.getBuyerDocumentType())) {
            throw new IllegalArgumentException("Factura requires a RUC buyer document");
        }
        Instant now = Instant.now();
        Voucher voucher =
                Voucher.createNew(
                        sale.getId(), requestedType, sale.getBuyerDocumentType(), sale.getBuyerDocumentNumber(), now);
        VoucherRequest request = VoucherRequestMapper.toRequest(sale);

        IssuedVoucher issued;
        try {
            issued = requestedType == VoucherType.FACTURA
                    ? invoicingProvider.issueFactura(request)
                    : invoicingProvider.issueBoleta(request);
        } catch (RuntimeException ex) {
            return voucherRepository.save(voucher.pending(ex.getMessage(), now));
        }

        if (issued.accepted()) {
            return voucherRepository.save(
                    voucher.accepted(
                            issued.voucherReference(), issued.pdfLink(), issued.xmlLink(), issued.cdrLink(),
                            issued.hash(), now));
        }
        return voucherRepository.save(voucher.pending(issued.sunatDescription(), now));
    }
}
