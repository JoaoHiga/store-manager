package com.storemanager.purchasing.usecase;

import com.storemanager.purchasing.domain.Supplier;
import com.storemanager.purchasing.domain.port.SupplierRepositoryPort;
import java.time.Instant;

public class DeactivateSupplierUseCase {

    private final SupplierRepositoryPort supplierRepository;

    public DeactivateSupplierUseCase(SupplierRepositoryPort supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    /** Idempotent by construction: deactivating an already-inactive supplier just re-saves it as inactive. */
    public void execute(Long supplierId) {
        Supplier existing =
                supplierRepository.findById(supplierId).orElseThrow(() -> new SupplierNotFoundException(supplierId));
        supplierRepository.save(existing.deactivated(Instant.now()));
    }
}
