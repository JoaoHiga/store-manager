package com.storemanager.purchasing.usecase;

import com.storemanager.catalog.domain.Product;
import com.storemanager.catalog.domain.port.ProductRepositoryPort;
import com.storemanager.purchasing.domain.Purchase;
import com.storemanager.purchasing.domain.PurchaseItem;
import com.storemanager.purchasing.domain.Supplier;
import com.storemanager.purchasing.domain.port.PurchaseRepositoryPort;
import com.storemanager.purchasing.domain.port.SupplierRepositoryPort;
import com.storemanager.shared.domain.port.TransactionRunner;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Records merchandise bought from a supplier and brings it into stock, all in one transaction
 * ({@link TransactionRunner}): validate that the supplier exists and is active, that every product exists
 * and is active, and that the supplier's invoice number is not already registered; save the
 * {@link Purchase}, snapshotting the supplier and product names; then, per line, increment the product's
 * stock and recompute its weighted-average cost price with one conditional update. Everything commits
 * together or not at all.
 *
 * <p>The conditional update is the real guard, as in {@code RecordSaleUseCase}: it computes the new stock
 * and cost from the row's current values, so a concurrent sale is never lost, and it refuses an inactive
 * product, so a product deactivated after the earlier check fails the update and rolls the whole purchase
 * back.
 */
public class RecordPurchaseUseCase {

    /** The store's time zone: "today" for the no-future-date rule is the date in Peru, not the server's. */
    public static final ZoneId STORE_ZONE = ZoneId.of("America/Lima");

    private final PurchaseRepositoryPort purchaseRepository;
    private final SupplierRepositoryPort supplierRepository;
    private final ProductRepositoryPort productRepository;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    /** {@code clock} gives "now" and, read in {@link #STORE_ZONE}, the store's "today". */
    public RecordPurchaseUseCase(
            PurchaseRepositoryPort purchaseRepository,
            SupplierRepositoryPort supplierRepository,
            ProductRepositoryPort productRepository,
            TransactionRunner transactionRunner,
            Clock clock) {
        this.purchaseRepository = purchaseRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    public Purchase execute(
            Long supplierId, String invoiceNumber, LocalDate purchaseDate, List<PurchaseLineItemRequest> lines) {
        return transactionRunner.inTransaction(() -> recordPurchase(supplierId, invoiceNumber, purchaseDate, lines));
    }

    private Purchase recordPurchase(
            Long supplierId, String invoiceNumber, LocalDate purchaseDate, List<PurchaseLineItemRequest> lines) {
        if (supplierId == null) {
            throw new IllegalArgumentException("Purchase supplierId is required");
        }
        Supplier supplier =
                supplierRepository.findById(supplierId).orElseThrow(() -> new SupplierNotFoundException(supplierId));
        if (!supplier.isActive()) {
            throw new InactiveSupplierException(supplierId);
        }

        List<PurchaseItem> items = lines == null ? List.of() : lines.stream().map(this::toPurchaseItem).toList();
        Instant now = clock.instant();
        Purchase purchase =
                Purchase.createNew(
                        supplier.getId(), supplier.getName(), invoiceNumber, purchaseDate, items,
                        LocalDate.ofInstant(now, STORE_ZONE), now);

        // Gives a precise error for the common case; the unique constraint behind save() is the race-safe guard.
        if (purchaseRepository.existsBySupplierIdAndInvoiceNumber(supplier.getId(), purchase.getInvoiceNumber())) {
            throw new DuplicatePurchaseInvoiceException(supplier.getId(), purchase.getInvoiceNumber());
        }
        Purchase saved = purchaseRepository.save(purchase);

        for (PurchaseItem item : purchase.getItems()) {
            if (!productRepository.incrementStockAndAverageCost(
                    item.getProductId(), item.getQuantity(), item.getUnitCost(), now)) {
                throw new InactiveProductException(item.getProductId());
            }
        }
        return saved;
    }

    private PurchaseItem toPurchaseItem(PurchaseLineItemRequest line) {
        if (line.productId() == null) {
            throw new IllegalArgumentException("PurchaseItem productId is required");
        }
        Product product =
                productRepository
                        .findById(line.productId())
                        .orElseThrow(() -> new ProductNotFoundException(line.productId()));
        if (!product.isActive()) {
            throw new InactiveProductException(product.getId());
        }
        return PurchaseItem.createNew(product.getId(), product.getName(), line.quantity(), line.unitCost());
    }
}
