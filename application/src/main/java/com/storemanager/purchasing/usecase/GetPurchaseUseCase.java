package com.storemanager.purchasing.usecase;

import com.storemanager.purchasing.domain.Purchase;
import com.storemanager.purchasing.domain.port.PurchaseRepositoryPort;

public class GetPurchaseUseCase {

    private final PurchaseRepositoryPort purchaseRepository;

    public GetPurchaseUseCase(PurchaseRepositoryPort purchaseRepository) {
        this.purchaseRepository = purchaseRepository;
    }

    public Purchase execute(Long id) {
        return purchaseRepository.findById(id).orElseThrow(() -> new PurchaseNotFoundException(id));
    }
}
