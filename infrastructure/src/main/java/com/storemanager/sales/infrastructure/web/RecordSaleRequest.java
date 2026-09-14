package com.storemanager.sales.infrastructure.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * {@code buyerDocumentType}/{@code buyerDocumentNumber} are optional (buyer-identification-hardening):
 * a sale under S/700 may omit both entirely ("boleta simple"). Real validation — the S/700
 * threshold and DNI/RUC/CE/Pasaporte format rules — is domain-side, in {@code
 * BuyerDocumentValidator} via {@code Sale}'s constructor, not enforced here at the DTO layer.
 */
public record RecordSaleRequest(
        @NotEmpty @Valid List<SaleItemRequest> items, String buyerDocumentType, String buyerDocumentNumber) {}
