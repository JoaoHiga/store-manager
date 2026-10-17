package com.storemanager.purchasing.infrastructure.web;

import com.storemanager.purchasing.domain.Purchase;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** A purchase with its lines: the record and detail response. */
public record PurchaseResponse(
        Long id,
        Long supplierId,
        String supplierName,
        String invoiceNumber,
        LocalDate purchaseDate,
        BigDecimal total,
        Instant createdAt,
        List<PurchaseItemResponse> items) {

    public static PurchaseResponse from(Purchase purchase) {
        return new PurchaseResponse(
                purchase.getId(),
                purchase.getSupplierId(),
                purchase.getSupplierName(),
                purchase.getInvoiceNumber(),
                purchase.getPurchaseDate(),
                purchase.getTotal(),
                purchase.getCreatedAt(),
                purchase.getItems().stream().map(PurchaseItemResponse::from).toList());
    }
}
