package com.storemanager.sales.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.storemanager.sales.domain.Sale;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * {@code voucherStatus} is always {@code null} (and so omitted from the JSON, via
 * {@code @JsonInclude(NON_NULL)}) in this slice — invoicing isn't wired in until a later PR (see
 * proposal.md, Phase 2+); this is a known, documented gap, not a bug.
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
        String voucherStatus) {

    public static SaleResponse from(Sale sale) {
        return new SaleResponse(
                sale.getId(),
                sale.getItems().stream().map(SaleItemResponse::from).toList(),
                sale.getBuyerDocumentType(),
                sale.getBuyerDocumentNumber(),
                sale.getTotal(),
                sale.getStatus().name(),
                sale.getCreatedAt(),
                null);
    }
}
