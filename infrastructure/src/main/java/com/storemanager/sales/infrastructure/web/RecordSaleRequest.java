package com.storemanager.sales.infrastructure.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record RecordSaleRequest(
        @NotEmpty @Valid List<SaleItemRequest> items,
        @NotBlank String buyerDocumentType,
        @NotBlank String buyerDocumentNumber) {}
