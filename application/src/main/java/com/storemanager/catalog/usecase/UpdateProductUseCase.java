package com.storemanager.catalog.usecase;

import com.storemanager.catalog.domain.Product;
import com.storemanager.catalog.domain.port.CategoryRepositoryPort;
import com.storemanager.catalog.domain.port.ProductRepositoryPort;
import java.math.BigDecimal;
import java.time.Instant;

public class UpdateProductUseCase {

    private final ProductRepositoryPort productRepository;
    private final CategoryRepositoryPort categoryRepository;

    public UpdateProductUseCase(ProductRepositoryPort productRepository, CategoryRepositoryPort categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    /**
     * {@code sku}, {@code stockQuantity} and {@code costPrice} are intentionally not parameters — none of them
     * can be changed through this use case. Stock only moves through atomic stock operations (sales,
     * purchases); the cost is set at creation and afterwards only moves as the weighted average of purchases.
     */
    public Product execute(
            Long productId,
            String name,
            String description,
            Long categoryId,
            String brand,
            BigDecimal salePrice) {
        Product existing =
                productRepository.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
        categoryRepository.findById(categoryId).orElseThrow(() -> new CategoryNotFoundException(categoryId));

        Product updated =
                existing.withUpdatedFields(name, description, categoryId, brand, salePrice, Instant.now());
        return productRepository.save(updated);
    }
}
