package com.storemanager.catalog.usecase;

import com.storemanager.catalog.domain.Product;
import com.storemanager.catalog.domain.port.ProductRepositoryPort;
import java.math.BigDecimal;
import java.time.Instant;

public class UpdateProductUseCase {

    private final ProductRepositoryPort productRepository;

    public UpdateProductUseCase(ProductRepositoryPort productRepository) {
        this.productRepository = productRepository;
    }

    /** {@code sku} is intentionally not a parameter — it can never be changed through this use case. */
    public Product execute(
            Long productId,
            String name,
            String description,
            Long categoryId,
            String brand,
            BigDecimal costPrice,
            BigDecimal salePrice,
            int stockQuantity) {
        Product existing =
                productRepository.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));

        Product updated =
                existing.withUpdatedFields(
                        name, description, categoryId, brand, costPrice, salePrice, stockQuantity, Instant.now());
        return productRepository.save(updated);
    }
}
