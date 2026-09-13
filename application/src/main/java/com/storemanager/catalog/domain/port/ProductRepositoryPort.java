package com.storemanager.catalog.domain.port;

import com.storemanager.catalog.domain.Product;
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
}
