package com.storemanager.catalog.infrastructure.persistence;

import com.storemanager.catalog.domain.Product;
import com.storemanager.catalog.domain.port.ProductRepositoryPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ProductRepositoryAdapter implements ProductRepositoryPort {

    private final ProductJpaRepository jpaRepository;

    public ProductRepositoryAdapter(ProductJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Product save(Product product) {
        String sku = product.getSku() != null ? product.getSku() : generateSku();
        ProductJpaEntity entity =
                new ProductJpaEntity(
                        product.getId(),
                        sku,
                        product.getName(),
                        product.getDescription(),
                        product.getCategoryId(),
                        product.getBrand(),
                        product.getCostPrice(),
                        product.getSalePrice(),
                        product.getStockQuantity(),
                        product.isActive(),
                        product.getCreatedAt(),
                        product.getUpdatedAt());
        ProductJpaEntity saved = jpaRepository.save(entity);
        if (product.getId() == null) {
            return toDomain(saved);
        }
        // Stock and cost are not updatable, so the merged entity still carries the caller's values, which may be
        // stale (a sale or purchase since the product was loaded). Report what the row holds instead.
        PersistedStockAndCost persisted = jpaRepository.findPersistedStockAndCost(saved.getId());
        return toDomain(saved, persisted.stockQuantity(), persisted.costPrice());
    }

    @Override
    public Optional<Product> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Product> findByActive(boolean active) {
        return jpaRepository.findByActive(active).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Product> findByActiveAndCategoryId(boolean active, Long categoryId) {
        return jpaRepository.findByActiveAndCategoryId(active, categoryId).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean decrementStockIfAvailable(Long productId, int quantity, Instant now) {
        return jpaRepository.decrementStockIfAvailable(productId, quantity, now) == 1;
    }

    @Override
    public boolean incrementStockAndAverageCost(Long productId, int quantity, BigDecimal unitCost, Instant now) {
        return jpaRepository.incrementStockAndAverageCost(productId, quantity, unitCost, now) == 1;
    }

    @Override
    public boolean incrementStock(Long productId, int quantity, Instant now) {
        return jpaRepository.incrementStock(productId, quantity, now) == 1;
    }

    private String generateSku() {
        return "PRD-%06d".formatted(jpaRepository.nextSkuSequenceValue());
    }

    private Product toDomain(ProductJpaEntity entity) {
        return toDomain(entity, entity.getStockQuantity(), entity.getCostPrice());
    }

    private Product toDomain(ProductJpaEntity entity, int stockQuantity, BigDecimal costPrice) {
        return Product.reconstitute(
                entity.getId(),
                entity.getSku(),
                entity.getName(),
                entity.getDescription(),
                entity.getCategoryId(),
                entity.getBrand(),
                costPrice,
                entity.getSalePrice(),
                stockQuantity,
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
