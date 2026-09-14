package com.storemanager.sales.usecase;

import com.storemanager.sales.domain.Sale;
import com.storemanager.sales.domain.port.SaleRepositoryPort;
import java.util.List;

public class ListSalesUseCase {

    private final SaleRepositoryPort saleRepository;

    public ListSalesUseCase(SaleRepositoryPort saleRepository) {
        this.saleRepository = saleRepository;
    }

    public List<Sale> execute() {
        return saleRepository.findAll();
    }
}
