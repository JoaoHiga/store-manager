package com.storemanager.purchasing.infrastructure.persistence;

import com.storemanager.purchasing.domain.Supplier;
import com.storemanager.purchasing.domain.port.SupplierRepositoryPort;
import com.storemanager.purchasing.usecase.DuplicateSupplierRucException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class SupplierRepositoryAdapter implements SupplierRepositoryPort {

    /** Declared in V9__create_suppliers.sql. */
    private static final String RUC_CONSTRAINT = "uq_suppliers_ruc";

    private final SupplierJpaRepository jpaRepository;

    public SupplierRepositoryAdapter(SupplierJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Supplier save(Supplier supplier) {
        SupplierJpaEntity entity =
                new SupplierJpaEntity(
                        supplier.getId(),
                        supplier.getName(),
                        supplier.getRuc(),
                        supplier.getPhone(),
                        supplier.getEmail(),
                        supplier.getAddress(),
                        supplier.isActive(),
                        supplier.getCreatedAt(),
                        supplier.getUpdatedAt());
        try {
            return toDomain(jpaRepository.save(entity));
        } catch (DataIntegrityViolationException ex) {
            // The use case's findByRuc check-then-act isn't race-safe; the unique constraint on
            // suppliers.ruc is the real guard. Only that constraint means a duplicate RUC; anything else propagates.
            if (violates(ex, RUC_CONSTRAINT)) {
                throw new DuplicateSupplierRucException(supplier.getRuc());
            }
            throw ex;
        }
    }

    /** H2 upper-cases constraint names in its messages and PostgreSQL does not, so compare case-insensitively. */
    private static boolean violates(DataIntegrityViolationException ex, String constraintName) {
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains(constraintName);
    }

    @Override
    public Optional<Supplier> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Supplier> findByRuc(String ruc) {
        return jpaRepository.findByRuc(ruc).map(this::toDomain);
    }

    @Override
    public List<Supplier> findAll() {
        return jpaRepository.findAllByOrderByNameAsc().stream().map(this::toDomain).toList();
    }

    @Override
    public List<Supplier> findByActive(boolean active) {
        return jpaRepository.findByActiveOrderByNameAsc(active).stream().map(this::toDomain).toList();
    }

    private Supplier toDomain(SupplierJpaEntity entity) {
        return Supplier.reconstitute(
                entity.getId(),
                entity.getName(),
                entity.getRuc(),
                entity.getPhone(),
                entity.getEmail(),
                entity.getAddress(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
