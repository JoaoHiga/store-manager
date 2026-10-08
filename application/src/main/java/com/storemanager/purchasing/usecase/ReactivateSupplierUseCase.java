package com.storemanager.purchasing.usecase;

import com.storemanager.purchasing.domain.Supplier;
import com.storemanager.purchasing.domain.port.SupplierRepositoryPort;
import java.time.Instant;

public class ReactivateSupplierUseCase {

    private final SupplierRepositoryPort supplierRepository;

    public ReactivateSupplierUseCase(SupplierRepositoryPort supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    /** Idempotent by construction: reactivating an already-active supplier just re-saves it as active. */
    public void execute(Long supplierId) {
        Supplier existing =
                supplierRepository.findById(supplierId).orElseThrow(() -> new SupplierNotFoundException(supplierId));
        supplierRepository.save(existing.activated(Instant.now()));
    }
}
