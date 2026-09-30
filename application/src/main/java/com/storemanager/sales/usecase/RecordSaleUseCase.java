package com.storemanager.sales.usecase;

import com.storemanager.catalog.domain.Product;
import com.storemanager.catalog.domain.port.ProductRepositoryPort;
import com.storemanager.invoicing.domain.Voucher;
import com.storemanager.invoicing.domain.VoucherType;
import com.storemanager.invoicing.usecase.IssueVoucherForSaleUseCase;
import com.storemanager.sales.domain.Sale;
import com.storemanager.sales.domain.SaleItem;
import com.storemanager.sales.domain.port.SaleRepositoryPort;
import com.storemanager.shared.domain.port.TransactionRunner;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records a sale against the existing product catalog, in two phases.
 *
 * <p><b>Phase 1, one transaction</b> ({@link TransactionRunner}): validate that every referenced product
 * exists, is active and has enough stock (quantities summed per product across lines, so two lines for the
 * same product cannot jointly oversell); save the {@link Sale}, snapshotting each product's current price
 * and description onto its {@link SaleItem}; decrement stock with a conditional update; and register the
 * voucher as {@code PENDING}. All of it commits together or not at all, so there is never a sale without its
 * stock decremented or without its voucher. The conditional decrement is the real stock guard: the earlier
 * availability check only produces a precise error message, and a concurrent sale that takes the stock in
 * between makes the decrement fail, which rolls the whole sale back.
 *
 * <p><b>Phase 2, after commit</b>: attempt voucher issuance once through {@link IssueVoucherForSaleUseCase}.
 * It calls NubeFacT, so it deliberately runs outside the transaction. It never blocks or undoes the sale:
 * any failure leaves the voucher {@code PENDING} for {@code RetryPendingVouchersJob}. This is a transactional
 * outbox (the {@code PENDING} voucher row is the outbox entry), not a saga: a sale is never compensated
 * because SUNAT is unavailable. See {@code docs/architecture.md}.
 */
public class RecordSaleUseCase {

    private static final System.Logger LOG = System.getLogger(RecordSaleUseCase.class.getName());

    private final SaleRepositoryPort saleRepository;
    private final ProductRepositoryPort productRepository;
    private final IssueVoucherForSaleUseCase issueVoucherForSaleUseCase;
    private final TransactionRunner transactionRunner;

    public RecordSaleUseCase(
            SaleRepositoryPort saleRepository,
            ProductRepositoryPort productRepository,
            IssueVoucherForSaleUseCase issueVoucherForSaleUseCase,
            TransactionRunner transactionRunner) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
        this.issueVoucherForSaleUseCase = issueVoucherForSaleUseCase;
        this.transactionRunner = transactionRunner;
    }

    public Sale execute(
            List<SaleLineItemRequest> lines,
            String buyerDocumentType,
            String buyerDocumentNumber,
            VoucherType voucherType) {
        // Reject invalid requests before opening a transaction. RecordSaleRequest's @NotNull already keeps a
        // null voucherType out over REST, but this use case is also called directly (tests, future callers).
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

        RecordedSale recorded =
                transactionRunner.inTransaction(
                        () -> recordSale(lines, requestedQuantityByProductId, buyerDocumentType, buyerDocumentNumber,
                                voucherType));

        try {
            issueVoucherForSaleUseCase.issue(recorded.pendingVoucher(), recorded.sale());
        } catch (RuntimeException ex) {
            // The sale is already committed and its voucher is PENDING, so the retry job will issue it. Only a
            // failure to persist the issuance outcome reaches here; provider failures are handled by issue().
            LOG.log(System.Logger.Level.WARNING,
                    "Voucher issuance for sale " + recorded.sale().getId() + " left for the retry job", ex);
        }
        return recorded.sale();
    }

    private RecordedSale recordSale(
            List<SaleLineItemRequest> lines,
            Map<Long, Integer> requestedQuantityByProductId,
            String buyerDocumentType,
            String buyerDocumentNumber,
            VoucherType voucherType) {
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
        Sale saved = saleRepository.save(Sale.createNew(items, buyerDocumentType, buyerDocumentNumber, Instant.now()));

        Instant now = Instant.now();
        for (Map.Entry<Long, Integer> requested : requestedQuantityByProductId.entrySet()) {
            Long productId = requested.getKey();
            if (!productRepository.decrementStockIfAvailable(productId, requested.getValue(), now)) {
                int available =
                        productRepository.findById(productId).map(Product::getStockQuantity).orElse(0);
                throw new InsufficientStockException(productId, requested.getValue(), available);
            }
        }

        Voucher pendingVoucher = issueVoucherForSaleUseCase.registerPending(saved, voucherType);
        return new RecordedSale(saved, pendingVoucher);
    }

    private SaleItem toSaleItem(SaleLineItemRequest line, Product product) {
        return SaleItem.createNew(product.getId(), product.getDescription(), product.getSalePrice(), line.quantity());
    }

    private record RecordedSale(Sale sale, Voucher pendingVoucher) {}
}
