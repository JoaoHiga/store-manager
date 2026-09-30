package com.storemanager.invoicing.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import com.storemanager.invoicing.domain.Voucher;
import com.storemanager.invoicing.domain.VoucherStatus;
import com.storemanager.invoicing.domain.VoucherType;
import com.storemanager.invoicing.usecase.RetryPendingVoucherUseCase;
import com.storemanager.sales.domain.Sale;
import com.storemanager.sales.domain.SaleItem;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RetryPendingVouchersJobTest {

    /** Well past the job's grace period: a voucher whose last attempt already failed a while ago. */
    private static final Instant TEN_MINUTES_AGO = Instant.now().minus(Duration.ofMinutes(10));

    private FakeInvoicingProvider invoicingProvider;
    private InMemoryVoucherRepository voucherRepository;
    private InMemorySaleRepository saleRepository;
    private RetryPendingVouchersJob job;

    @BeforeEach
    void setUp() {
        invoicingProvider = new FakeInvoicingProvider();
        voucherRepository = new InMemoryVoucherRepository();
        saleRepository = new InMemorySaleRepository();
        RetryPendingVoucherUseCase retryPendingVoucherUseCase =
                new RetryPendingVoucherUseCase(voucherRepository, saleRepository, invoicingProvider);
        job = new RetryPendingVouchersJob(voucherRepository, retryPendingVoucherUseCase);
    }

    private Long seedSale() {
        return saleRepository
                .save(
                        Sale.createNew(
                                List.of(SaleItem.createNew(1L, "Usado", new BigDecimal("100"), 1)), "DNI",
                                "12345678", Instant.now()))
                .getId();
    }

    private Voucher seedPendingVoucher(Long saleId, int attemptCount) {
        return seedPendingVoucher(saleId, attemptCount, TEN_MINUTES_AGO);
    }

    private Voucher seedPendingVoucher(Long saleId, int attemptCount, Instant updatedAt) {
        return voucherRepository.save(
                Voucher.reconstitute(
                        null, saleId, VoucherType.BOLETA, VoucherStatus.PENDING, "DNI", "12345678", attemptCount,
                        "prior failure", null, null, null, null, null, updatedAt, updatedAt));
    }

    @Test
    void retriesEveryPendingVoucherAndLeavesAcceptedOnesUntouched() {
        Long saleId = seedSale();
        Voucher pending = seedPendingVoucher(saleId, 1);
        invoicingProvider.alwaysAccept();

        job.retryPendingVouchers();

        Voucher retried = voucherRepository.findById(pending.getId()).orElseThrow();
        assertThat(retried.getStatus()).isEqualTo(VoucherStatus.ACCEPTED);
    }

    @Test
    void doesNotRetryVouchersThatAreNotPending() {
        Long saleId = seedSale();
        Voucher accepted =
                voucherRepository.save(
                        Voucher.reconstitute(
                                null, saleId, VoucherType.BOLETA, VoucherStatus.ACCEPTED, "DNI", "12345678", 0, null,
                                "REF-1", "pdf", "xml", "cdr", "hash", Instant.now(), Instant.now()));
        invoicingProvider.alwaysThrow();

        job.retryPendingVouchers();

        Voucher unchanged = voucherRepository.findById(accepted.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(VoucherStatus.ACCEPTED);
    }

    @Test
    void oneVoucherFailingToRetryDoesNotStopTheRestOfTheBatch() {
        // Seed a PENDING voucher whose sale no longer resolves, forcing RetryPendingVoucherUseCase
        // to throw SaleNotFoundException for it; a second, resolvable PENDING voucher must still be
        // retried and succeed — proves the job isolates failures per voucher instead of aborting
        // the whole scheduled run on the first error.
        Voucher orphanVoucher =
                voucherRepository.save(
                        Voucher.reconstitute(
                                null, 999L, VoucherType.BOLETA, VoucherStatus.PENDING, "DNI", "12345678", 0,
                                "prior failure", null, null, null, null, null, TEN_MINUTES_AGO, TEN_MINUTES_AGO));
        Long saleId = seedSale();
        Voucher healthyVoucher = seedPendingVoucher(saleId, 0);
        invoicingProvider.alwaysAccept();

        job.retryPendingVouchers();

        Voucher stillPending = voucherRepository.findById(orphanVoucher.getId()).orElseThrow();
        assertThat(stillPending.getStatus()).isEqualTo(VoucherStatus.PENDING);
        Voucher accepted = voucherRepository.findById(healthyVoucher.getId()).orElseThrow();
        assertThat(accepted.getStatus()).isEqualTo(VoucherStatus.ACCEPTED);
    }

    /**
     * atomic-record-sale: RecordSaleUseCase commits a PENDING voucher and only then calls NubeFacT. A voucher
     * touched moments ago may be mid-issuance right now; retrying it here would issue it twice.
     */
    @Test
    void skipsAPendingVoucherUpdatedWithinTheGracePeriod() {
        Long saleId = seedSale();
        Voucher justRegistered = seedPendingVoucher(saleId, 0, Instant.now());
        invoicingProvider.alwaysAccept();

        job.retryPendingVouchers();

        Voucher untouched = voucherRepository.findById(justRegistered.getId()).orElseThrow();
        assertThat(untouched.getStatus()).isEqualTo(VoucherStatus.PENDING);
        assertThat(untouched.getAttemptCount()).isZero();
    }
}
