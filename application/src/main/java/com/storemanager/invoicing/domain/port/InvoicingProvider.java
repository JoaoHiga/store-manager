package com.storemanager.invoicing.domain.port;

import com.storemanager.invoicing.domain.VoucherStatus;

/**
 * Outbound port to an OSE (NubeFacT, per design.md) that issues and manages SUNAT electronic
 * documents. Framework-free and OSE-agnostic: nothing about NubeFacT's literal field names or
 * HTTP shape belongs here or in any caller — {@code NubeFactInvoicingAdapter} (PR3) is the only
 * class that knows about NubeFacT.
 */
public interface InvoicingProvider {

    IssuedVoucher issueBoleta(VoucherRequest request);

    IssuedVoucher issueFactura(VoucherRequest request);

    IssuedVoucher issueCreditNote(CreditNoteRequest request);

    VoucherStatus checkStatus(String voucherReference);

    IssuedVoucher voidVoucher(String voucherReference, String reason);
}
