package com.storemanager.invoicing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.storemanager.invoicing.domain.Voucher;
import com.storemanager.invoicing.domain.VoucherStatus;
import com.storemanager.invoicing.domain.VoucherType;
import com.storemanager.sales.domain.Sale;
import com.storemanager.sales.domain.SaleItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RetryPendingVoucherUseCaseTest {

    private FakeInvoicingProvider invoicingProvider;
    private InMemoryVoucherRepository voucherRepository;
    private InMemorySaleRepository saleRepository;
    private RetryPendingVoucherUseCase useCase;
    private Long saleId;

    @BeforeEach
    void setUp() {
        invoicingProvider = new FakeInvoicingProvider();
        voucherRepository = new InMemoryVoucherRepository();
        saleRepository = new InMemorySaleRepository();
        useCase = new RetryPendingVoucherUseCase(voucherRepository, saleRepository, invoicingProvider);

        List<SaleItem> items = List.of(SaleItem.createNew(1L, "Usado", new BigDecimal("100"), 2));
        Sale sale = saleRepository.save(Sale.createNew(items, "DNI", "12345678", Instant.now()));
        saleId = sale.getId();
    }

    private Voucher seedVoucher(VoucherStatus status, int attemptCount) {
        Voucher voucher =
                Voucher.reconstitute(
                        null, saleId, VoucherType.BOLETA, status, "DNI", "12345678", attemptCount, "prior failure",
                        null, null, null, null, null, Instant.now(), Instant.now());
        // InMemoryVoucherRepository only assigns an id when the given id is null, and it always
        // starts a fresh voucher at PENDING/attempt 0 via reconstitute passthrough here since we
        // already built the exact status/attemptCount we want above.
        return voucherRepository.save(voucher);
    }

    @Test
    void aPendingVoucherRetriedAutomaticallyAndAcceptedTransitionsToAccepted() {
        Voucher voucher = seedVoucher(VoucherStatus.PENDING, 0);
        invoicingProvider.alwaysAccept();

        Voucher retried = useCase.execute(voucher.getId());

        assertThat(retried.getStatus()).isEqualTo(VoucherStatus.ACCEPTED);
    }

    @Test
    void aPendingVoucherStillFailingUnderTheCapStaysPendingWithIncrementedAttemptCount() {
        Voucher voucher = seedVoucher(VoucherStatus.PENDING, 2);
        invoicingProvider.alwaysReject("still invalid");

        Voucher retried = useCase.execute(voucher.getId());

        assertThat(retried.getStatus()).isEqualTo(VoucherStatus.PENDING);
        assertThat(retried.getAttemptCount()).isEqualTo(3);
        assertThat(retried.getRejectionReason()).isEqualTo("still invalid");
    }

    @Test
    void aPendingVoucherExhaustsAutomaticRetriesAndEscalatesToNeedsAttention() {
        Voucher voucher = seedVoucher(VoucherStatus.PENDING, RetryPendingVoucherUseCase.MAX_RETRY_ATTEMPTS - 1);
        invoicingProvider.alwaysThrow();

        Voucher retried = useCase.execute(voucher.getId());

        assertThat(retried.getStatus()).isEqualTo(VoucherStatus.NEEDS_ATTENTION);
        assertThat(retried.getAttemptCount()).isEqualTo(RetryPendingVoucherUseCase.MAX_RETRY_ATTEMPTS);
    }

    @Test
    void aManualRetrySucceedsAfterExhaustion() {
        Voucher voucher = seedVoucher(VoucherStatus.NEEDS_ATTENTION, RetryPendingVoucherUseCase.MAX_RETRY_ATTEMPTS);
        invoicingProvider.alwaysAccept();

        Voucher retried = useCase.execute(voucher.getId());

        assertThat(retried.getStatus()).isEqualTo(VoucherStatus.ACCEPTED);
    }

    @Test
    void aManualRetryThatFailsAgainStaysNeedsAttentionWithoutFurtherIncrement() {
        Voucher voucher = seedVoucher(VoucherStatus.NEEDS_ATTENTION, RetryPendingVoucherUseCase.MAX_RETRY_ATTEMPTS);
        invoicingProvider.alwaysReject("still broken");

        Voucher retried = useCase.execute(voucher.getId());

        assertThat(retried.getStatus()).isEqualTo(VoucherStatus.NEEDS_ATTENTION);
        assertThat(retried.getAttemptCount()).isEqualTo(RetryPendingVoucherUseCase.MAX_RETRY_ATTEMPTS);
        assertThat(retried.getRejectionReason()).isEqualTo("still broken");
    }

    @Test
    void retryingAnAcceptedVoucherIsRejected() {
        Voucher voucher = seedVoucher(VoucherStatus.ACCEPTED, 0);

        assertThatThrownBy(() -> useCase.execute(voucher.getId())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void retryingAnUnknownVoucherIsRejected() {
        assertThatThrownBy(() -> useCase.execute(999L)).isInstanceOf(VoucherNotFoundException.class);
    }
}
