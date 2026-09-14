package com.storemanager.sales.domain.port;

import com.storemanager.sales.domain.Sale;
import java.util.List;
import java.util.Optional;

public interface SaleRepositoryPort {

    Sale save(Sale sale);

    Optional<Sale> findById(Long id);

    List<Sale> findAll();
}
