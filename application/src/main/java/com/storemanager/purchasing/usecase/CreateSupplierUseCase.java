package com.storemanager.purchasing.usecase;

import com.storemanager.purchasing.domain.Supplier;
import com.storemanager.purchasing.domain.port.SupplierRepositoryPort;
import java.time.Instant;

public class CreateSupplierUseCase {

    private final SupplierRepositoryPort supplierRepository;

    public CreateSupplierUseCase(SupplierRepositoryPort supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    public Supplier execute(String name, String ruc, String phone, String email, String address) {
        Supplier supplier = Supplier.createNew(name, ruc, phone, email, address, Instant.now());

        supplierRepository
                .findByRuc(ruc)
                .ifPresent(
                        existing -> {
                            throw new DuplicateSupplierRucException(ruc);
                        });

        return supplierRepository.save(supplier);
    }
}
