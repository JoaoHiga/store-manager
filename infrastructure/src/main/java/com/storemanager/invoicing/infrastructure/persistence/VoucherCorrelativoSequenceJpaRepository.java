package com.storemanager.invoicing.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoucherCorrelativoSequenceJpaRepository
        extends JpaRepository<VoucherCorrelativoSequenceJpaEntity, String> {

    /** Locks the row for the duration of the caller's transaction so two concurrent allocations for the same series never hand out the same correlativo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from VoucherCorrelativoSequenceJpaEntity s where s.series = :series")
    Optional<VoucherCorrelativoSequenceJpaEntity> findBySeriesForUpdate(@Param("series") String series);
}
