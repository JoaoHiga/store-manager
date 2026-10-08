package com.storemanager.purchasing.domain.port;

import com.storemanager.purchasing.domain.Supplier;
import java.util.List;
import java.util.Optional;

/**
 * Output port for Supplier persistence. Implemented by {@code SupplierRepositoryAdapter} in the
 * infrastructure module. Both list methods return suppliers ordered by name.
 */
public interface SupplierRepositoryPort {

    Supplier save(Supplier supplier);

    Optional<Supplier> findById(Long id);

    Optional<Supplier> findByRuc(String ruc);

    List<Supplier> findAll();

    List<Supplier> findByActive(boolean active);
}
