package com.storemanager.catalog.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, Long> {

    List<ProductJpaEntity> findByActive(boolean active);

    List<ProductJpaEntity> findByActiveAndCategoryId(boolean active, Long categoryId);

    /** Next value from the dedicated {@code product_sku_seq} (see V1__init_catalog.sql). */
    @Query(value = "SELECT nextval('product_sku_seq')", nativeQuery = true)
    long nextSkuSequenceValue();

    /**
     * Stock and cost read from the row, never from an entity held in the persistence context: those two columns
     * are not updatable, so an entity saved from stale values keeps the stale values in memory.
     */
    @Query(
            "SELECT new com.storemanager.catalog.infrastructure.persistence.PersistedStockAndCost("
                    + "p.stockQuantity, p.costPrice) FROM ProductJpaEntity p WHERE p.id = :productId")
    PersistedStockAndCost findPersistedStockAndCost(@Param("productId") Long productId);

    /**
     * Conditional, single-statement stock decrement: the database checks and subtracts against the current
     * row, so two concurrent sales can never both take the last unit. Returns the number of rows updated,
     * 0 when the stock is insufficient. {@code clearAutomatically} evicts stale cached entities so later
     * reads in the same persistence context see the new stock. {@code @Transactional} joins the caller's
     * transaction when there is one (the normal case, via {@code SpringTransactionRunner}).
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            "UPDATE ProductJpaEntity p SET p.stockQuantity = p.stockQuantity - :quantity, p.updatedAt = :now"
                    + " WHERE p.id = :productId AND p.stockQuantity >= :quantity")
    int decrementStockIfAvailable(
            @Param("productId") Long productId, @Param("quantity") int quantity, @Param("now") Instant now);

    /**
     * Single-statement purchase entry: adds {@code quantity} to the stock and sets the cost price to the
     * weighted average of the current stock at its current cost and the new units at {@code unitCost}, or to
     * {@code unitCost} when there is no cost or no stock yet. Like the decrement, it works on the current row
     * (a concurrent sale is never lost) and returns 0 when the product is missing or inactive.
     *
     * <p>Portability (H2 in tests, PostgreSQL in production): standard SQL evaluates every {@code SET}
     * right-hand side against the pre-update row, so the cost uses the stock <em>before</em> this increment
     * regardless of assignment order. The average is computed in {@code NUMERIC} (cost is {@code NUMERIC(12,2)},
     * {@code unitCost} binds as {@code NUMERIC}), so the division keeps its fractional digits on both
     * databases, and {@code round(numeric, 2)} rounds half away from zero, which is half-up for these
     * non-negative values. {@code ProductRepositoryAdapterTest} and {@code PurchaseConcurrencyPostgresTest}
     * pin this behavior on H2 and on PostgreSQL.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            "UPDATE ProductJpaEntity p SET"
                    + " p.costPrice = CASE WHEN p.costPrice IS NULL OR p.stockQuantity = 0 THEN :unitCost"
                    + " ELSE round((p.stockQuantity * p.costPrice + :quantity * :unitCost)"
                    + " / (p.stockQuantity + :quantity), 2) END,"
                    + " p.stockQuantity = p.stockQuantity + :quantity,"
                    + " p.updatedAt = :now"
                    + " WHERE p.id = :productId AND p.active = true")
    int incrementStockAndAverageCost(
            @Param("productId") Long productId,
            @Param("quantity") int quantity,
            @Param("unitCost") BigDecimal unitCost,
            @Param("now") Instant now);
}
