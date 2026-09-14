package com.storemanager.invoicing.domain.port;

/**
 * The {@link InvoicingProvider}'s response to an issue/void call. {@code accepted} is {@code
 * true} only when SUNAT accepted the document through the OSE; {@code sunatDescription}/{@code
 * sunatCode} carry the OSE's own status message (an acceptance note, or a rejection reason) —
 * {@code voucherReference} is our own stable handle for later {@code checkStatus}/{@code
 * voidVoucher} calls, not necessarily the OSE's literal series+correlativo (series/correlativo
 * assignment is deferred to PR3's adapter, see design.md's open questions). The PDF/XML/CDR links
 * and {@code hash} are populated only when {@code accepted} is {@code true}.
 */
public record IssuedVoucher(
        boolean accepted,
        String voucherReference,
        String sunatDescription,
        String sunatCode,
        String hash,
        String pdfLink,
        String xmlLink,
        String cdrLink) {}
