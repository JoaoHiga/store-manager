package com.storemanager.invoicing.domain.port;

import com.storemanager.invoicing.domain.Voucher;
import java.util.List;
import java.util.Optional;

public interface VoucherRepositoryPort {

    Voucher save(Voucher voucher);

    Optional<Voucher> findById(Long id);

    Optional<Voucher> findBySaleId(Long saleId);

    /** Every {@code PENDING} voucher — what {@code RetryPendingVouchersJob} (PR4) polls. */
    List<Voucher> findAllPending();
}
