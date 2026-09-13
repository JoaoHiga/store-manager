package com.storemanager.catalog.usecase;

import com.storemanager.catalog.domain.Product;
import com.storemanager.catalog.domain.port.ProductRepositoryPort;
import java.time.Instant;

public class DeactivateProductUseCase {

    private final ProductRepositoryPort productRepository;

    public DeactivateProductUseCase(ProductRepositoryPort productRepository) {
        this.productRepository = productRepository;
    }

    /** Idempotent by construction: deactivating an already-inactive product just re-saves it as inactive. */
    public void execute(Long productId) {
        Product existing =
                productRepository.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
        productRepository.save(existing.deactivated(Instant.now()));
    }
}
