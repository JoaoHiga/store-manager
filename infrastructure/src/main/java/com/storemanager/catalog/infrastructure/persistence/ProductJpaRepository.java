package com.storemanager.catalog.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, Long> {

    List<ProductJpaEntity> findByActiveTrue();

    List<ProductJpaEntity> findByActiveTrueAndCategoryId(Long categoryId);

    /** Next value from the dedicated {@code product_sku_seq} (see V1__init_catalog.sql). */
    @Query(value = "SELECT nextval('product_sku_seq')", nativeQuery = true)
    long nextSkuSequenceValue();
}
