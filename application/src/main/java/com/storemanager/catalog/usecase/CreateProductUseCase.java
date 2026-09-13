package com.storemanager.catalog.usecase;

import com.storemanager.catalog.domain.Product;
import com.storemanager.catalog.domain.port.CategoryRepositoryPort;
import com.storemanager.catalog.domain.port.ProductRepositoryPort;
import java.math.BigDecimal;
import java.time.Instant;

public class CreateProductUseCase {

    private final ProductRepositoryPort productRepository;
    private final CategoryRepositoryPort categoryRepository;

    public CreateProductUseCase(ProductRepositoryPort productRepository, CategoryRepositoryPort categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    public Product execute(
            String name,
            String description,
            Long categoryId,
            String brand,
            BigDecimal costPrice,
            BigDecimal salePrice,
            int stockQuantity) {
        categoryRepository.findById(categoryId).orElseThrow(() -> new CategoryNotFoundException(categoryId));

        Product product =
                Product.createNew(
                        name, description, categoryId, brand, costPrice, salePrice, stockQuantity, Instant.now());
        return productRepository.save(product);
    }
}
