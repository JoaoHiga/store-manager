package com.storemanager.purchasing.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseJpaRepository extends JpaRepository<PurchaseJpaEntity, Long> {

    List<PurchaseJpaEntity> findAllByOrderByPurchaseDateDescIdDesc();

    boolean existsBySupplierIdAndInvoiceNumber(Long supplierId, String invoiceNumber);
}
