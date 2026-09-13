package com.storemanager.catalog.infrastructure.web;

import com.storemanager.catalog.domain.Product;
import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        Long categoryId,
        String brand,
        BigDecimal costPrice,
        BigDecimal salePrice,
        int stockQuantity,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
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
    }
}
