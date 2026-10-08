package com.storemanager.sales.usecase;

import com.storemanager.catalog.domain.Product;
import com.storemanager.catalog.domain.port.ProductRepositoryPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * In-memory {@link ProductRepositoryPort} test double for {@code sales} use-case tests. Mirrors
 * {@code com.storemanager.catalog.usecase.InMemoryProductRepository} — kept as its own copy
 * rather than reused across packages because that one is package-private, and each bounded
 * context owns its own test doubles (see proposal.md, point 5).
 */
class InMemoryProductRepository implements ProductRepositoryPort {

    private final List<Product> products = new ArrayList<>();
    private long nextId = 1;
    private long nextSku = 1;
    private boolean stockLostToAConcurrentSale = false;

    @Override
    public Product save(Product product) {
        Product toPersist = product;
        if (product.getId() == null) {
            toPersist =
                    Product.reconstitute(
                            nextId++,
                            "PRD-%06d".formatted(nextSku++),
                            product.getName(),
                            product.getDescription(),
                            product.getCategoryId(),
                            product.getBrand(),
                            product.getCostPrice(),
                            product.getSalePrice(),
                            product.getStockQuantity(),
                            product.isActive(),
                            product.getCreatedAt(),
                            product.getUpdatedAt());
            products.add(toPersist);
            return toPersist;
        }
        products.removeIf(p -> p.getId().equals(product.getId()));
        products.add(product);
        return product;
    }

    @Override
    public Optional<Product> findById(Long id) {
        return products.stream().filter(p -> p.getId().equals(id)).findFirst();
    }

    @Override
    public List<Product> findByActive(boolean active) {
        return products.stream().filter(p -> p.isActive() == active).toList();
    }

    @Override
    public List<Product> findByActiveAndCategoryId(boolean active, Long categoryId) {
        return products.stream()
                .filter(p -> p.isActive() == active)
                .filter(p -> p.getCategoryId().equals(categoryId))
                .toList();
    }

    /**
     * Makes the next {@link #decrementStockIfAvailable} call fail as if a concurrent sale had taken the stock
     * between {@code RecordSaleUseCase}'s availability check and its conditional decrement.
     */
    void loseStockToAConcurrentSaleOnNextDecrement() {
        stockLostToAConcurrentSale = true;
    }

    @Override
    public boolean decrementStockIfAvailable(Long productId, int quantity, Instant now) {
        if (stockLostToAConcurrentSale) {
            stockLostToAConcurrentSale = false;
            return false;
        }
        Optional<Product> current = findById(productId);
        if (current.isEmpty() || current.get().getStockQuantity() < quantity) {
            return false;
        }
        Product product = current.get();
        save(
                Product.reconstitute(
                        product.getId(), product.getSku(), product.getName(), product.getDescription(),
                        product.getCategoryId(), product.getBrand(), product.getCostPrice(), product.getSalePrice(),
                        product.getStockQuantity() - quantity, product.isActive(), product.getCreatedAt(), now));
        return true;
    }
}
