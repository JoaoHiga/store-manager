package com.storemanager.catalog.usecase;

import com.storemanager.catalog.domain.Product;
import com.storemanager.catalog.domain.port.ProductRepositoryPort;
import java.util.List;

public class ListProductsUseCase {

    private final ProductRepositoryPort productRepository;

    public ListProductsUseCase(ProductRepositoryPort productRepository) {
        this.productRepository = productRepository;
    }

    /** {@code categoryId == null} lists every active product, regardless of category. */
    public List<Product> execute(Long categoryId) {
        if (categoryId != null) {
            return productRepository.findActiveByCategoryId(categoryId);
        }
        return productRepository.findAllActive();
    }
}
