package com.storemanager.invoicing.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoucherJpaRepository extends JpaRepository<VoucherJpaEntity, Long> {

    Optional<VoucherJpaEntity> findBySaleId(Long saleId);

    List<VoucherJpaEntity> findByStatus(String status);
}
