package com.storemanager.catalog.usecase;

import com.storemanager.catalog.domain.Product;
import com.storemanager.catalog.domain.port.ProductRepositoryPort;
import java.time.Instant;

public class ReactivateProductUseCase {

    private final ProductRepositoryPort productRepository;

    public ReactivateProductUseCase(ProductRepositoryPort productRepository) {
        this.productRepository = productRepository;
    }

    /** Idempotent by construction: reactivating an already-active product just re-saves it as active. */
    public void execute(Long productId) {
        Product existing =
                productRepository.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
        productRepository.save(existing.activated(Instant.now()));
    }
}
