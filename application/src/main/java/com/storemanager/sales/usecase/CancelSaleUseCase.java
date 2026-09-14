package com.storemanager.sales.usecase;

import com.storemanager.invoicing.usecase.VoidSaleUseCase;
import com.storemanager.sales.domain.Sale;
import com.storemanager.sales.domain.port.SaleRepositoryPort;
import java.time.Instant;

/**
 * Voids a sale's voucher and marks the sale itself {@code CANCELLED} — the half of the void flow
 * that {@link VoidSaleUseCase} (PR2) explicitly deferred (see its javadoc's "Known PR4 dependency"
 * and design.md's void path: "Voucher -&gt; VOIDED, Sale -&gt; CANCELLED"). Kept as one use case
 * (used by {@code POST /api/sales/{id}/void}, PR4) rather than two separate controller calls, so a
 * single request leaves both records consistent: if the voucher isn't voidable (not {@code
 * ACCEPTED}) or the OSE doesn't confirm the void, {@code VoidSaleUseCase} throws and the sale is
 * never touched — it only ever becomes {@code CANCELLED} once the voucher is confirmed {@code
 * VOIDED}. The sale is resolved BEFORE calling {@code VoidSaleUseCase} (not after) — per RDD
 * review-reliability finding R3-cancel-not-atomic — so a {@code SaleNotFoundException} can never
 * happen only after the irreversible OSE call already succeeded.
 */
public class CancelSaleUseCase {

    private final VoidSaleUseCase voidSaleUseCase;
    private final SaleRepositoryPort saleRepository;

    public CancelSaleUseCase(VoidSaleUseCase voidSaleUseCase, SaleRepositoryPort saleRepository) {
        this.voidSaleUseCase = voidSaleUseCase;
        this.saleRepository = saleRepository;
    }

    public Sale execute(Long saleId, String reason) {
        Sale sale = saleRepository.findById(saleId).orElseThrow(() -> new SaleNotFoundException(saleId));
        voidSaleUseCase.execute(saleId, reason);
        try {
            return saleRepository.save(sale.cancelled(Instant.now()));
        } catch (RuntimeException ex) {
            throw new SaleCancellationInconsistentException(saleId, ex);
        }
    }
}
