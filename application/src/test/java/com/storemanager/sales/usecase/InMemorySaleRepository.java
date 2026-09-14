package com.storemanager.sales.usecase;

import com.storemanager.sales.domain.Sale;
import com.storemanager.sales.domain.SaleItem;
import com.storemanager.sales.domain.port.SaleRepositoryPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** In-memory {@link SaleRepositoryPort} test double — mirrors catalog's fake-repository pattern. */
class InMemorySaleRepository implements SaleRepositoryPort {

    private final List<Sale> sales = new ArrayList<>();
    private long nextSaleId = 1;
    private long nextItemId = 1;
    private boolean failOnNextSave = false;

    /** Simulates a transient DB failure on the very next {@link #save} call only. */
    void failOnNextSave() {
        this.failOnNextSave = true;
    }

    @Override
    public Sale save(Sale sale) {
        if (failOnNextSave) {
            failOnNextSave = false;
            throw new RuntimeException("Simulated database failure while saving sale " + sale.getId());
        }
        if (sale.getId() != null) {
            sales.removeIf(s -> s.getId().equals(sale.getId()));
            sales.add(sale);
            return sale;
        }
        List<SaleItem> persistedItems =
                sale.getItems().stream()
                        .map(
                                item ->
                                        SaleItem.reconstitute(
                                                nextItemId++,
                                                item.getProductId(),
                                                item.getDescription(),
                                                item.getUnitPrice(),
                                                item.getQuantity()))
                        .toList();
        Sale persisted =
                Sale.reconstitute(
                        nextSaleId++,
                        persistedItems,
                        sale.getBuyerDocumentType(),
                        sale.getBuyerDocumentNumber(),
                        sale.getStatus(),
                        sale.getCreatedAt());
        sales.add(persisted);
        return persisted;
    }

    @Override
    public Optional<Sale> findById(Long id) {
        return sales.stream().filter(s -> s.getId().equals(id)).findFirst();
    }

    @Override
    public List<Sale> findAll() {
        return List.copyOf(sales);
    }
}
