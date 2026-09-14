package com.storemanager.invoicing.infrastructure.nubefact;

import com.storemanager.invoicing.infrastructure.persistence.VoucherCorrelativoSequenceJpaEntity;
import com.storemanager.invoicing.infrastructure.persistence.VoucherCorrelativoSequenceJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Durable, database-backed {@link CorrelativoAllocator}: row locked for the whole transaction, surviving restarts and multiple app instances. */
@Component
public class JpaCorrelativoAllocator implements CorrelativoAllocator {

    private final VoucherCorrelativoSequenceJpaRepository repository;

    public JpaCorrelativoAllocator(VoucherCorrelativoSequenceJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public <T> T withCorrelativo(String series, CorrelativoOperation<T> operation) {
        // Row must pre-exist (seeded by V5__...sql): FOR UPDATE locks nothing on an absent row.
        // Lock is held for this whole transaction, including while `operation` runs; a thrown
        // exception rolls the transaction back, leaving the counter untouched.
        VoucherCorrelativoSequenceJpaEntity sequence =
                repository
                        .findBySeriesForUpdate(series)
                        .orElseThrow(
                                () -> new IllegalStateException("No correlativo sequence seeded for series: " + series));
        long correlativo = sequence.getNextCorrelativo();
        T result = operation.perform(correlativo);
        sequence.setNextCorrelativo(correlativo + 1);
        repository.save(sequence);
        return result;
    }
}
