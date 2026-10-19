package com.storemanager.catalog.domain.port;

import com.storemanager.catalog.domain.Product;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProductRepositoryPort {

    /**
     * Persists a product. When {@code product.getSku()} is {@code null}
     * (a brand-new product), the adapter assigns one from the dedicated SKU
     * sequence as part of this call.
     */
    Product save(Product product);

    Optional<Product> findById(Long id);

    List<Product> findByActive(boolean active);

    List<Product> findByActiveAndCategoryId(boolean active, Long categoryId);

    /**
     * Atomically subtracts {@code quantity} from the product's stock, only if at least that much is
     * available, as a single conditional update against the current stored value (never a read-modify-write
     * of a previously loaded {@link Product}). Returns {@code false}, changing nothing, when the stock is
     * insufficient at the moment of the update, e.g. because a concurrent sale took it first.
     */
    boolean decrementStockIfAvailable(Long productId, int quantity, Instant now);

    /**
     * Atomically adds {@code quantity} to an active product's stock and recomputes its cost price as the
     * weighted average {@code (stock * costPrice + quantity * unitCost) / (stock + quantity)}, rounded half-up
     * to 2 decimals; when the product has no cost price yet or no stock, the new cost price is
     * {@code unitCost}. Stock and cost are computed from the current stored values in a single conditional
     * update (never a read-modify-write of a previously loaded {@link Product}), so a concurrent sale or
     * purchase is never lost. Returns {@code false}, changing nothing, when the product is missing or
     * inactive at the moment of the update.
     */
    boolean incrementStockAndAverageCost(Long productId, int quantity, BigDecimal unitCost, Instant now);

    /**
     * Atomically adds {@code quantity} back to the product's stock (for example when a completed sale is
     * cancelled), regardless of whether the product is currently active or inactive, without changing its
     * {@code costPrice}. Returns {@code false}, changing nothing, when the product does not exist.
     */
    boolean incrementStock(Long productId, int quantity, Instant now);
}
