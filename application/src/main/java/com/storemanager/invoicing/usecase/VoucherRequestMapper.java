package com.storemanager.invoicing.usecase;

import com.storemanager.invoicing.domain.port.VoucherLineItem;
import com.storemanager.invoicing.domain.port.VoucherRequest;
import com.storemanager.sales.domain.Sale;

/**
 * Builds the {@link VoucherRequest} sent to {@code InvoicingProvider} from a {@link Sale}. Shared
 * by {@link IssueVoucherForSaleUseCase} and {@link RetryPendingVoucherUseCase} so a retry rebuilds
 * the exact same request shape as the original issuance attempt.
 */
final class VoucherRequestMapper {

    private VoucherRequestMapper() {}

    static VoucherRequest toRequest(Sale sale) {
        return new VoucherRequest(
                sale.getBuyerDocumentType(),
                sale.getBuyerDocumentNumber(),
                sale.getItems().stream()
                        .map(
                                item ->
                                        new VoucherLineItem(
                                                item.getDescription(), item.getUnitPrice(), item.getQuantity(),
                                                item.getSubtotal()))
                        .toList(),
                sale.getTotal());
    }
}
