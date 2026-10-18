package com.storemanager.purchasing.usecase;

import com.storemanager.purchasing.domain.Purchase;
import com.storemanager.purchasing.domain.port.PurchaseRepositoryPort;
import java.util.List;

public class ListPurchasesUseCase {

    private final PurchaseRepositoryPort purchaseRepository;

    public ListPurchasesUseCase(PurchaseRepositoryPort purchaseRepository) {
        this.purchaseRepository = purchaseRepository;
    }

    /** Newest first. */
    public List<Purchase> execute() {
        return purchaseRepository.findAll();
    }
}
