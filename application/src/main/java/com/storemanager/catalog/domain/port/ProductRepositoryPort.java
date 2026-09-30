package com.storemanager.catalog.domain.port;

import com.storemanager.catalog.domain.Product;
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
}
