package com.storemanager.purchasing.infrastructure.web;

import com.storemanager.purchasing.domain.Purchase;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One row of the purchase list: the header plus how many lines it has, without the lines themselves. */
public record PurchaseSummaryResponse(
        Long id,
        Long supplierId,
        String supplierName,
        String invoiceNumber,
        LocalDate purchaseDate,
        BigDecimal total,
        int lineCount,
        Instant createdAt) {

    public static PurchaseSummaryResponse from(Purchase purchase) {
        return new PurchaseSummaryResponse(
                purchase.getId(),
                purchase.getSupplierId(),
                purchase.getSupplierName(),
                purchase.getInvoiceNumber(),
                purchase.getPurchaseDate(),
                purchase.getTotal(),
                purchase.getItems().size(),
                purchase.getCreatedAt());
    }
}
