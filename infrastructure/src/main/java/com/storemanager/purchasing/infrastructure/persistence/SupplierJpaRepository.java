package com.storemanager.purchasing.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierJpaRepository extends JpaRepository<SupplierJpaEntity, Long> {

    Optional<SupplierJpaEntity> findByRuc(String ruc);

    List<SupplierJpaEntity> findAllByOrderByNameAsc();

    List<SupplierJpaEntity> findByActiveOrderByNameAsc(boolean active);
}
