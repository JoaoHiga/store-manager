package com.storemanager.purchasing.usecase;

import com.storemanager.purchasing.domain.Purchase;
import com.storemanager.purchasing.domain.PurchaseItem;
import com.storemanager.purchasing.domain.port.PurchaseRepositoryPort;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** In-memory {@link PurchaseRepositoryPort} test double, same approach as the other purchasing fakes. */
class InMemoryPurchaseRepository implements PurchaseRepositoryPort {

    private final List<Purchase> purchases = new ArrayList<>();
    private long nextId = 1;
    private long nextItemId = 1;

    @Override
    public Purchase save(Purchase purchase) {
        List<PurchaseItem> items =
                purchase.getItems().stream()
                        .map(
                                item ->
                                        PurchaseItem.reconstitute(
                                                nextItemId++, item.getProductId(), item.getProductName(),
                                                item.getQuantity(), item.getUnitCost()))
                        .toList();
        Purchase persisted =
                Purchase.reconstitute(
                        nextId++, purchase.getSupplierId(), purchase.getSupplierName(), purchase.getInvoiceNumber(),
                        purchase.getPurchaseDate(), items, purchase.getCreatedAt());
        purchases.add(persisted);
        return persisted;
    }

    @Override
    public Optional<Purchase> findById(Long id) {
        return purchases.stream().filter(p -> p.getId().equals(id)).findFirst();
    }

    @Override
    public List<Purchase> findAll() {
        return purchases.stream()
                .sorted(
                        Comparator.comparing(Purchase::getPurchaseDate)
                                .thenComparing(Purchase::getId)
                                .reversed())
                .toList();
    }

    @Override
    public boolean existsBySupplierIdAndInvoiceNumber(Long supplierId, String invoiceNumber) {
        return purchases.stream()
                .anyMatch(p -> p.getSupplierId().equals(supplierId) && p.getInvoiceNumber().equals(invoiceNumber));
    }
}
