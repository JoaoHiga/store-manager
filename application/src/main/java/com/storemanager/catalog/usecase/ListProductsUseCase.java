package com.storemanager.catalog.usecase;

import com.storemanager.catalog.domain.Product;
import com.storemanager.catalog.domain.port.ProductRepositoryPort;
import java.util.List;

public class ListProductsUseCase {

    private final ProductRepositoryPort productRepository;

    public ListProductsUseCase(ProductRepositoryPort productRepository) {
        this.productRepository = productRepository;
    }

    /** {@code categoryId == null} lists across every category. {@code active} selects which set (default true). */
    public List<Product> execute(Long categoryId, boolean active) {
        if (categoryId != null) {
            return productRepository.findByActiveAndCategoryId(active, categoryId);
        }
        return productRepository.findByActive(active);
    }
}
