package com.storemanager.purchasing.infrastructure.persistence;

import com.storemanager.purchasing.domain.Purchase;
import com.storemanager.purchasing.domain.PurchaseItem;
import com.storemanager.purchasing.domain.port.PurchaseRepositoryPort;
import com.storemanager.purchasing.usecase.DuplicatePurchaseInvoiceException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class PurchaseRepositoryAdapter implements PurchaseRepositoryPort {

    /** Declared in V10__create_purchases.sql. */
    private static final String SUPPLIER_INVOICE_CONSTRAINT = "uq_purchases_supplier_invoice";

    private final PurchaseJpaRepository jpaRepository;

    public PurchaseRepositoryAdapter(PurchaseJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Purchase save(Purchase purchase) {
        List<PurchaseItemJpaEntity> items =
                purchase.getItems().stream()
                        .map(
                                item ->
                                        new PurchaseItemJpaEntity(
                                                item.getId(),
                                                item.getProductId(),
                                                item.getProductName(),
                                                item.getQuantity(),
                                                item.getUnitCost(),
                                                item.getSubtotal()))
                        .toList();
        PurchaseJpaEntity entity =
                new PurchaseJpaEntity(
                        purchase.getId(),
                        purchase.getSupplierId(),
                        purchase.getSupplierName(),
                        purchase.getInvoiceNumber(),
                        purchase.getPurchaseDate(),
                        purchase.getTotal(),
                        purchase.getCreatedAt(),
                        items);
        try {
            return toDomain(jpaRepository.save(entity));
        } catch (DataIntegrityViolationException ex) {
            // The use case's exists check isn't race-safe; the unique (supplier_id, invoice_number) constraint
            // is the real guard. Only that constraint means a duplicate invoice; anything else propagates.
            if (violates(ex, SUPPLIER_INVOICE_CONSTRAINT)) {
                throw new DuplicatePurchaseInvoiceException(purchase.getSupplierId(), purchase.getInvoiceNumber());
            }
            throw ex;
        }
    }

    /** H2 upper-cases constraint names in its messages and PostgreSQL does not, so compare case-insensitively. */
    private static boolean violates(DataIntegrityViolationException ex, String constraintName) {
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains(constraintName);
    }

    @Override
    public Optional<Purchase> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Purchase> findAll() {
        return jpaRepository.findAllByOrderByPurchaseDateDescIdDesc().stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsBySupplierIdAndInvoiceNumber(Long supplierId, String invoiceNumber) {
        return jpaRepository.existsBySupplierIdAndInvoiceNumber(supplierId, invoiceNumber);
    }

    private Purchase toDomain(PurchaseJpaEntity entity) {
        List<PurchaseItem> items =
                entity.getItems().stream()
                        .map(
                                item ->
                                        PurchaseItem.reconstitute(
                                                item.getId(),
                                                item.getProductId(),
                                                item.getProductName(),
                                                item.getQuantity(),
                                                item.getUnitCost()))
                        .toList();
        return Purchase.reconstitute(
                entity.getId(),
                entity.getSupplierId(),
                entity.getSupplierName(),
                entity.getInvoiceNumber(),
                entity.getPurchaseDate(),
                items,
                entity.getCreatedAt());
    }
}
