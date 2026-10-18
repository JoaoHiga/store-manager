package com.storemanager.catalog.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "products")
public class ProductJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String sku;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(length = 80)
    private String brand;

    /**
     * Written only on INSERT (the cost entered at creation). After that, the cost changes exclusively as the
     * weighted average computed by the JPQL bulk update {@link ProductJpaRepository#incrementStockAndAverageCost}
     * (bulk updates ignore {@code updatable}); saving an entity loaded earlier (product edit, deactivate,
     * activate) must never write back a stale cost over a purchase.
     */
    @Column(name = "cost_price", precision = 12, scale = 2, updatable = false)
    private BigDecimal costPrice;

    @Column(name = "sale_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal salePrice;

    /**
     * Written only on INSERT (initial stock). After that, stock changes exclusively through atomic,
     * conditional JPQL updates such as {@link ProductJpaRepository#decrementStockIfAvailable}; saving an
     * entity loaded earlier (product edit, deactivate, activate) must never write back a stale stock.
     */
    @Column(name = "stock_quantity", nullable = false, updatable = false)
    private int stockQuantity;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductJpaEntity() {
        // required by JPA
    }

    public ProductJpaEntity(
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
