package com.storemanager.sales.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.storemanager.invoicing.domain.Voucher;
import java.time.Instant;

/** Response shape for {@code POST /api/sales/{id}/retry-voucher} — surfaces the voucher's current state after the retry attempt. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VoucherResponse(
        Long id,
        Long saleId,
        String type,
        String status,
        int attemptCount,
        String rejectionReason,
        String externalReference,
        String pdfLink,
        String xmlLink,
        String cdrLink,
        String hash,
        Instant createdAt,
        Instant updatedAt) {

    public static VoucherResponse from(Voucher voucher) {
        return new VoucherResponse(
                voucher.getId(),
                voucher.getSaleId(),
                voucher.getType().name(),
                voucher.getStatus().name(),
                voucher.getAttemptCount(),
                voucher.getRejectionReason(),
                voucher.getExternalReference(),
                voucher.getPdfLink(),
                voucher.getXmlLink(),
                voucher.getCdrLink(),
                voucher.getHash(),
                voucher.getCreatedAt(),
                voucher.getUpdatedAt());
    }
}
