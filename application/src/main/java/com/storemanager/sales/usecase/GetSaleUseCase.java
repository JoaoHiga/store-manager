package com.storemanager.sales.usecase;

import com.storemanager.sales.domain.Sale;
import com.storemanager.sales.domain.port.SaleRepositoryPort;

public class GetSaleUseCase {

    private final SaleRepositoryPort saleRepository;

    public GetSaleUseCase(SaleRepositoryPort saleRepository) {
        this.saleRepository = saleRepository;
    }

    public Sale execute(Long id) {
        return saleRepository.findById(id).orElseThrow(() -> new SaleNotFoundException(id));
    }
}
