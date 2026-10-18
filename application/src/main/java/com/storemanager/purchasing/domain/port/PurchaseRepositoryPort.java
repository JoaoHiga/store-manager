package com.storemanager.purchasing.domain.port;

import com.storemanager.purchasing.domain.Purchase;
import java.util.List;
import java.util.Optional;

/**
 * Output port for Purchase persistence. Implemented by {@code PurchaseRepositoryAdapter} in the
 * infrastructure module.
 */
public interface PurchaseRepositoryPort {

    /**
     * Persists a new purchase with its items. Throws {@code DuplicatePurchaseInvoiceException} when the
     * supplier already has a purchase with the same invoice number, including one committed concurrently.
     */
    Purchase save(Purchase purchase);

    Optional<Purchase> findById(Long id);

    /** Every purchase, newest first: by purchase date descending, then by id descending. */
    List<Purchase> findAll();

    boolean existsBySupplierIdAndInvoiceNumber(Long supplierId, String invoiceNumber);
}
