package com.storemanager.catalog.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A catalog product. {@code sku} is {@code null} for a not-yet-persisted
 * product: it's assigned by the infrastructure adapter from a dedicated DB
 * sequence at save time, not by this class (see design.md, Decision: SKU
 * generation). {@code categoryId} referencing a real category is validated
 * by {@link com.storemanager.catalog.usecase.CreateProductUseCase} via
 * {@code CategoryRepositoryPort}, not here — this class only enforces its
 * own structural invariants.
 */
public final class Product {

    private final Long id;
    private final String sku;
    private final String name;
    private final String description;
    private final Long categoryId;
    private final String brand;
    private final BigDecimal costPrice;
    private final BigDecimal salePrice;
    private final int stockQuantity;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Product(
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
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Product name must not be blank");
        }
        if (categoryId == null) {
            throw new IllegalArgumentException("Product categoryId is required");
        }
        if (salePrice == null || salePrice.signum() <= 0) {
            throw new IllegalArgumentException("Product salePrice must be greater than zero");
        }
        if (stockQuantity < 0) {
            throw new IllegalArgumentException("Product stockQuantity must not be negative");
        }
        this.id = id;
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.categoryId = categoryId;
        this.brand = brand;
        this.costPrice = costPrice;
        this.salePrice = salePrice;
        this.stockQuantity = stockQuantity;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Product createNew(
            String name,
            String description,
            Long categoryId,
            String brand,
            BigDecimal costPrice,
            BigDecimal salePrice,
            int stockQuantity,
            Instant now) {
        return new Product(
                null, null, name, description, categoryId, brand, costPrice, salePrice, stockQuantity, true, now, now);
    }

    public static Product reconstitute(
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
        return new Product(
                id, sku, name, description, categoryId, brand, costPrice, salePrice, stockQuantity, active, createdAt,
                updatedAt);
    }

    public Product withSku(String sku) {
        return new Product(
                id, sku, name, description, categoryId, brand, costPrice, salePrice, stockQuantity, active, createdAt,
                updatedAt);
    }

    public Product withUpdatedFields(
            String name,
            String description,
            Long categoryId,
            String brand,
            BigDecimal costPrice,
            BigDecimal salePrice,
            int stockQuantity,
            Instant now) {
        return new Product(
                id, sku, name, description, categoryId, brand, costPrice, salePrice, stockQuantity, active, createdAt,
                now);
    }

    public Product deactivated(Instant now) {
        return new Product(
                id, sku, name, description, categoryId, brand, costPrice, salePrice, stockQuantity, false, createdAt,
                now);
    }

    public Product activated(Instant now) {
        return new Product(
                id, sku, name, description, categoryId, brand, costPrice, salePrice, stockQuantity, true, createdAt,
                now);
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public String getBrand() {
        return brand;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
