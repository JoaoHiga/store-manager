package com.storemanager.purchasing.usecase;

import com.storemanager.purchasing.domain.Supplier;
import com.storemanager.purchasing.domain.port.SupplierRepositoryPort;
import java.util.List;

public class ListSuppliersUseCase {

    private final SupplierRepositoryPort supplierRepository;

    public ListSuppliersUseCase(SupplierRepositoryPort supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    /** {@code active == null} lists every supplier; otherwise only that set. Always ordered by name. */
    public List<Supplier> execute(Boolean active) {
        if (active == null) {
            return supplierRepository.findAll();
        }
        return supplierRepository.findByActive(active);
    }
}
