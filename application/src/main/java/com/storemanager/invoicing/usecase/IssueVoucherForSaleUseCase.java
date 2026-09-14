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
 * "Voucher Issuance via OSE" requirements. A RUC buyer produces a {@code FACTURA}; any other
 * buyer document type produces a {@code BOLETA}. The sale itself always completes regardless of
 * the outcome here: any provider rejection, timeout, or thrown exception is caught and persisted
 * as a {@code PENDING} voucher (retaining the rejection reason) instead of propagating — never
 * blocking or rolling back the sale (see design.md's failure/retry path).
 */
public class IssueVoucherForSaleUseCase {

    private final VoucherRepositoryPort voucherRepository;
    private final InvoicingProvider invoicingProvider;

    public IssueVoucherForSaleUseCase(VoucherRepositoryPort voucherRepository, InvoicingProvider invoicingProvider) {
        this.voucherRepository = voucherRepository;
        this.invoicingProvider = invoicingProvider;
    }

    public Voucher execute(Sale sale) {
        VoucherType type = determineType(sale.getBuyerDocumentType());
        Instant now = Instant.now();
        Voucher voucher =
                Voucher.createNew(sale.getId(), type, sale.getBuyerDocumentType(), sale.getBuyerDocumentNumber(), now);
        VoucherRequest request = VoucherRequestMapper.toRequest(sale);

        IssuedVoucher issued;
        try {
            issued = type == VoucherType.FACTURA
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

    private VoucherType determineType(String buyerDocumentType) {
        return "RUC".equalsIgnoreCase(buyerDocumentType) ? VoucherType.FACTURA : VoucherType.BOLETA;
    }
}
