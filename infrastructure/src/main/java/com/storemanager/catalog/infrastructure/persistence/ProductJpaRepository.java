package com.storemanager.catalog.infrastructure.persistence;

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
}
