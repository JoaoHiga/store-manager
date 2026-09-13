package com.storemanager.catalog.infrastructure.persistence;

import com.storemanager.catalog.domain.Product;
import com.storemanager.catalog.domain.port.ProductRepositoryPort;
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
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<Product> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Product> findAllActive() {
        return jpaRepository.findByActiveTrue().stream().map(this::toDomain).toList();
    }

    @Override
    public List<Product> findActiveByCategoryId(Long categoryId) {
        return jpaRepository.findByActiveTrueAndCategoryId(categoryId).stream().map(this::toDomain).toList();
    }

    private String generateSku() {
        return "PRD-%06d".formatted(jpaRepository.nextSkuSequenceValue());
    }

    private Product toDomain(ProductJpaEntity entity) {
        return Product.reconstitute(
                entity.getId(),
                entity.getSku(),
                entity.getName(),
                entity.getDescription(),
                entity.getCategoryId(),
                entity.getBrand(),
                entity.getCostPrice(),
                entity.getSalePrice(),
                entity.getStockQuantity(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
