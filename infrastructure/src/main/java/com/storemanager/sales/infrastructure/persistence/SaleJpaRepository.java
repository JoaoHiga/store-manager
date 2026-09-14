package com.storemanager.sales.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleJpaRepository extends JpaRepository<SaleJpaEntity, Long> {}
