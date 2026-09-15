package com.storemanager.sales.infrastructure.web;

import com.storemanager.invoicing.domain.VoucherType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * {@code buyerDocumentType}/{@code buyerDocumentNumber} are optional (buyer-identification-hardening):
 * a sale under S/700 may omit both entirely ("boleta simple"). Real validation — the S/700
 * threshold and DNI/RUC/CE/Pasaporte format rules — is domain-side, in {@code
 * BuyerDocumentValidator} via {@code Sale}'s constructor, not enforced here at the DTO layer.
 *
 * <p>{@code voucherType} is a required explicit choice (explicit-voucher-type-selection): the
 * caller decides BOLETA vs. FACTURA at sale-recording time instead of it being derived from the
 * buyer document type. The one real constraint (FACTURA requires a RUC buyer) is domain-side, in
 * {@code IssueVoucherForSaleUseCase}, not enforced here at the DTO layer.
 */
public record RecordSaleRequest(
        @NotEmpty @Valid List<SaleItemRequest> items,
        String buyerDocumentType,
        String buyerDocumentNumber,
        @NotNull VoucherType voucherType) {}
