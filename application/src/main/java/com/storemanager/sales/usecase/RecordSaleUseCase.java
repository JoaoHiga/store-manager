package com.storemanager.sales.usecase;

import com.storemanager.catalog.domain.Product;
import com.storemanager.catalog.domain.port.ProductRepositoryPort;
import com.storemanager.invoicing.domain.VoucherType;
import com.storemanager.invoicing.usecase.IssueVoucherForSaleUseCase;
import com.storemanager.sales.domain.Sale;
import com.storemanager.sales.domain.SaleItem;
import com.storemanager.sales.domain.port.SaleRepositoryPort;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records a sale against the existing product catalog. For each requested line, validates the
 * referenced product exists, is active, and has enough stock via the existing
 * {@code ProductRepositoryPort} — {@code product-catalog}'s own code is never modified (see
 * proposal.md, Dependencies), but a completed sale DOES decrement stock through that same port,
 * since a sale is exactly the event that consumes it. Quantities are validated cumulatively per
 * product across every line in the request (not line-by-line against the untouched stock), so two
 * lines referencing the same product can't each pass an individual check while jointly overselling
 * — found and required by review-reliability's R3-stock-not-decremented finding. Then each
 * product's current {@code salePrice}/{@code description} is snapshotted onto its {@link SaleItem}
 * so a later catalog change never affects this sale (see design.md, Decision: Sale line-item
 * pricing).
 *
 * <p>Once the sale is persisted, this use case calls {@link IssueVoucherForSaleUseCase} directly
 * (PR4 of sales-invoicing) rather than through a separate orchestrating use case: design.md's Data
 * Flow shows voucher issuance as the immediate next step after "Sale persisted", with no
 * additional business rule of its own in between, so a wrapper use case here would only add
 * indirection — {@code RecordSaleUseCase} stays the app's single "record a sale" entry point,
 * which is what {@code SaleController} already calls. Per {@code IssueVoucherForSaleUseCase}'s own
 * contract it never throws and always persists either an {@code ACCEPTED} or {@code PENDING}
 * voucher, so a sale is never rolled back or blocked by an invoicing failure — its returned {@code
 * Voucher} is intentionally discarded here; voucher state is queried separately (by sale id) when
 * needed.
 */
public class RecordSaleUseCase {

    private final SaleRepositoryPort saleRepository;
    private final ProductRepositoryPort productRepository;
    private final IssueVoucherForSaleUseCase issueVoucherForSaleUseCase;

    public RecordSaleUseCase(
            SaleRepositoryPort saleRepository,
            ProductRepositoryPort productRepository,
            IssueVoucherForSaleUseCase issueVoucherForSaleUseCase) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
        this.issueVoucherForSaleUseCase = issueVoucherForSaleUseCase;
    }

    public Sale execute(
            List<SaleLineItemRequest> lines,
            String buyerDocumentType,
            String buyerDocumentNumber,
            VoucherType voucherType) {
        // Fail fast, before any persistence: IssueVoucherForSaleUseCase re-checks the FACTURA/RUC
        // rule and Voucher.createNew re-checks non-null type, but both run AFTER
        // saleRepository.save(sale) below, with no surrounding @Transactional boundary to roll
        // that save back. Checking both here too means a rejected request never leaves a phantom
        // COMPLETED sale row behind — RecordSaleRequest's @NotNull keeps a null voucherType from
        // ever reaching here over the REST API, but this use case is also called directly
        // (unit tests, any future caller), so it must not rely on that DTO-layer guard alone
        // (found by RDD's R3-voucher-validation-after-sale-persist and its follow-up
        // R3-null-voucher-type-unguarded — the request's own validation must happen before its
        // side effects, not after, and must cover every reachable invalid input, not just one).
        if (voucherType == null) {
            throw new IllegalArgumentException("Voucher type is required");
        }
        if (voucherType == VoucherType.FACTURA && !"RUC".equalsIgnoreCase(buyerDocumentType)) {
            throw new IllegalArgumentException("Factura requires a RUC buyer document");
        }

        Map<Long, Integer> requestedQuantityByProductId = new LinkedHashMap<>();
        for (SaleLineItemRequest line : lines) {
            requestedQuantityByProductId.merge(line.productId(), line.quantity(), Integer::sum);
        }

        Map<Long, Product> productsById = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> requested : requestedQuantityByProductId.entrySet()) {
            Long productId = requested.getKey();
            int totalRequestedQuantity = requested.getValue();
            Product product =
                    productRepository.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
            if (!product.isActive()) {
                throw new InactiveProductException(product.getId());
            }
            if (totalRequestedQuantity > product.getStockQuantity()) {
                throw new InsufficientStockException(
                        product.getId(), totalRequestedQuantity, product.getStockQuantity());
            }
            productsById.put(productId, product);
        }

        List<SaleItem> items =
                lines.stream().map(line -> toSaleItem(line, productsById.get(line.productId()))).toList();
        Sale sale = Sale.createNew(items, buyerDocumentType, buyerDocumentNumber, Instant.now());
        Sale saved = saleRepository.save(sale);

        issueVoucherForSaleUseCase.execute(saved, voucherType);

        Instant now = Instant.now();
        for (Map.Entry<Long, Integer> requested : requestedQuantityByProductId.entrySet()) {
            Product product = productsById.get(requested.getKey());
            int remainingStock = product.getStockQuantity() - requested.getValue();
            productRepository.save(
                    product.withUpdatedFields(
                            product.getName(),
                            product.getDescription(),
                            product.getCategoryId(),
                            product.getBrand(),
                            product.getCostPrice(),
                            product.getSalePrice(),
                            remainingStock,
                            now));
        }

        return saved;
    }

    private SaleItem toSaleItem(SaleLineItemRequest line, Product product) {
        return SaleItem.createNew(product.getId(), product.getDescription(), product.getSalePrice(), line.quantity());
    }
}
