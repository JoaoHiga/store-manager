package com.storemanager.invoicing.infrastructure.scheduling;

import com.storemanager.invoicing.domain.Voucher;
import com.storemanager.invoicing.domain.port.VoucherRepositoryPort;
import com.storemanager.invoicing.usecase.RetryPendingVoucherUseCase;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls every {@code PENDING} voucher and retries it through {@link RetryPendingVoucherUseCase},
 * per design.md's failure/retry path — runs every 5 minutes. {@code fixedDelay} (not {@code
 * fixedRate}) so a slow run can never overlap the next poll. Deliberately queries only {@code
 * PENDING} vouchers via {@code VoucherRepositoryPort#findAllPending}: {@code NEEDS_ATTENTION}
 * vouchers have already exhausted automatic retries ({@code RetryPendingVoucherUseCase#MAX_RETRY_ATTEMPTS})
 * and are excluded on purpose — only the manual "retry now" endpoint ({@code
 * RetrySaleVoucherUseCase}, PR4) can bring one of those back.
 *
 * <p>Each voucher is retried independently, catching any exception so one bad voucher (e.g. its
 * sale no longer resolves) can never abort the rest of the scheduled batch — the failure is logged
 * and the loop moves on.
 */
@Component
public class RetryPendingVouchersJob {

    private static final Logger log = LoggerFactory.getLogger(RetryPendingVouchersJob.class);

    private final VoucherRepositoryPort voucherRepository;
    private final RetryPendingVoucherUseCase retryPendingVoucherUseCase;

    public RetryPendingVouchersJob(
            VoucherRepositoryPort voucherRepository, RetryPendingVoucherUseCase retryPendingVoucherUseCase) {
        this.voucherRepository = voucherRepository;
        this.retryPendingVoucherUseCase = retryPendingVoucherUseCase;
    }

    @Scheduled(fixedDelay = 300000)
    public void retryPendingVouchers() {
        List<Voucher> pendingVouchers = voucherRepository.findAllPending();
        for (Voucher voucher : pendingVouchers) {
            try {
                retryPendingVoucherUseCase.execute(voucher.getId());
            } catch (RuntimeException ex) {
                log.warn("Scheduled retry failed for voucher {}", voucher.getId(), ex);
            }
        }
    }
}
