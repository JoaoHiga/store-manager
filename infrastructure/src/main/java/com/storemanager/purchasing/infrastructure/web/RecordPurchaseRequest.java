package com.storemanager.purchasing.infrastructure.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Shape checks only. The rules that need the store's date or other data (not in the future, same product
 * twice, supplier and products active, duplicate invoice) are enforced by the domain and the use case.
 */
public record RecordPurchaseRequest(
        @NotNull Long supplierId,
        @NotBlank @Size(max = 30) String invoiceNumber,
        @NotNull LocalDate purchaseDate,
        @NotEmpty @Valid List<PurchaseItemRequest> items) {}
