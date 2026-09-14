package com.storemanager.sales.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.storemanager.invoicing.domain.Voucher;
import com.storemanager.invoicing.domain.VoucherStatus;
import com.storemanager.sales.domain.Sale;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * {@code voucher} is {@code null} (SaleController's own query found none for this sale — should
 * not happen once PR4's synchronous issuance on record is wired, but the field stays nullable so a
 * sale is never made undisplayable by a missing/orphaned voucher row). {@code pdfLink}/{@code
 * xmlLink}/{@code cdrLink}/{@code hash} are populated only once the voucher is {@code ACCEPTED}
 * (spec.md, "List and View Sales").
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SaleResponse(
        Long id,
        List<SaleItemResponse> items,
        String buyerDocumentType,
        String buyerDocumentNumber,
        BigDecimal total,
        String status,
        Instant createdAt,
        String voucherStatus,
        String pdfLink,
        String xmlLink,
        String cdrLink,
        String hash) {

    public static SaleResponse from(Sale sale, Voucher voucher) {
        boolean accepted = voucher != null && voucher.getStatus() == VoucherStatus.ACCEPTED;
        return new SaleResponse(
                sale.getId(),
                sale.getItems().stream().map(SaleItemResponse::from).toList(),
                sale.getBuyerDocumentType(),
                sale.getBuyerDocumentNumber(),
                sale.getTotal(),
                sale.getStatus().name(),
                sale.getCreatedAt(),
                voucher != null ? voucher.getStatus().name() : null,
                accepted ? voucher.getPdfLink() : null,
                accepted ? voucher.getXmlLink() : null,
                accepted ? voucher.getCdrLink() : null,
                accepted ? voucher.getHash() : null);
    }
}
