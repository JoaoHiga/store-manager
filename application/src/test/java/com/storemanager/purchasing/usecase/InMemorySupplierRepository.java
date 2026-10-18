package com.storemanager.purchasing.usecase;

import com.storemanager.purchasing.domain.Supplier;
import com.storemanager.purchasing.domain.port.SupplierRepositoryPort;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** In-memory {@link SupplierRepositoryPort} test double, same approach as the catalog fakes. */
class InMemorySupplierRepository implements SupplierRepositoryPort {

    private final List<Supplier> suppliers = new ArrayList<>();
    private long nextId = 1;

    @Override
    public Supplier save(Supplier supplier) {
        Long id = supplier.getId() != null ? supplier.getId() : nextId++;
        Supplier persisted =
                Supplier.reconstitute(
                        id,
                        supplier.getName(),
                        supplier.getRuc(),
                        supplier.getPhone(),
                        supplier.getEmail(),
                        supplier.getAddress(),
                        supplier.isActive(),
                        supplier.getCreatedAt(),
                        supplier.getUpdatedAt());
        suppliers.removeIf(existing -> existing.getId().equals(id));
        suppliers.add(persisted);
        return persisted;
    }

    @Override
    public Optional<Supplier> findById(Long id) {
        return suppliers.stream().filter(s -> s.getId().equals(id)).findFirst();
    }

    @Override
    public Optional<Supplier> findByRuc(String ruc) {
        return suppliers.stream().filter(s -> s.getRuc().equals(ruc)).findFirst();
    }

    @Override
    public List<Supplier> findAll() {
        return suppliers.stream().sorted(Comparator.comparing(Supplier::getName)).toList();
    }

    @Override
    public List<Supplier> findByActive(boolean active) {
        return findAll().stream().filter(s -> s.isActive() == active).toList();
    }
}
