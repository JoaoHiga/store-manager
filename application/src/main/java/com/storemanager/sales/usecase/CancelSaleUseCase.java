package com.storemanager.sales.usecase;

import com.storemanager.catalog.domain.port.ProductRepositoryPort;
import com.storemanager.invoicing.usecase.VoidSaleUseCase;
import com.storemanager.sales.domain.Sale;
import com.storemanager.sales.domain.SaleItem;
import com.storemanager.sales.domain.port.SaleRepositoryPort;
import com.storemanager.shared.domain.port.TransactionRunner;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Voids a sale's voucher, marks the sale itself {@code CANCELLED}, and restores each sold product's
 * stock. Kept as one use case (used by {@code POST /api/sales/{id}/void}) rather than separate
 * controller calls, so a single request leaves the records consistent: if the voucher isn't
 * voidable (not {@code ACCEPTED}) or the OSE doesn't confirm the void, {@code VoidSaleUseCase}
 * throws and neither the sale nor product stock is touched. Once the voucher is confirmed {@code
 * VOIDED}, marking the sale {@code CANCELLED} and incrementing each product's stock run together in
 * one {@link TransactionRunner} transaction. The sale is resolved BEFORE calling {@code
 * VoidSaleUseCase} (not after) — per RDD review-reliability finding R3-cancel-not-atomic — so a
 * {@code SaleNotFoundException} can never happen only after the irreversible OSE call already
 * succeeded.
 */
public class CancelSaleUseCase {

    private final VoidSaleUseCase voidSaleUseCase;
    private final SaleRepositoryPort saleRepository;
    private final ProductRepositoryPort productRepository;
    private final TransactionRunner transactionRunner;

    public CancelSaleUseCase(
            VoidSaleUseCase voidSaleUseCase,
            SaleRepositoryPort saleRepository,
            ProductRepositoryPort productRepository,
            TransactionRunner transactionRunner) {
        this.voidSaleUseCase = voidSaleUseCase;
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
        this.transactionRunner = transactionRunner;
    }

    public Sale execute(Long saleId, String reason) {
        Sale sale = saleRepository.findById(saleId).orElseThrow(() -> new SaleNotFoundException(saleId));
        voidSaleUseCase.execute(saleId, reason);
        try {
            return transactionRunner.inTransaction(
                    () -> {
                        Instant now = Instant.now();
                        Sale cancelled = saleRepository.save(sale.cancelled(now));
                        restoreStock(sale, now);
                        return cancelled;
                    });
        } catch (RuntimeException ex) {
            throw new SaleCancellationInconsistentException(saleId, ex);
        }
    }

    private void restoreStock(Sale sale, Instant now) {
        Map<Long, Integer> quantityByProductId = new LinkedHashMap<>();
        for (SaleItem item : sale.getItems()) {
            quantityByProductId.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }
        for (Map.Entry<Long, Integer> entry : quantityByProductId.entrySet()) {
            if (!productRepository.incrementStock(entry.getKey(), entry.getValue(), now)) {
                throw new ProductNotFoundException(entry.getKey());
            }
        }
    }
}
