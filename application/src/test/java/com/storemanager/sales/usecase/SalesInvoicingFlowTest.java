package com.storemanager.sales.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.storemanager.catalog.domain.Product;
import com.storemanager.invoicing.domain.Voucher;
import com.storemanager.invoicing.domain.VoucherStatus;
import com.storemanager.invoicing.usecase.IssueVoucherForSaleUseCase;
import com.storemanager.invoicing.usecase.RetryPendingVoucherUseCase;
import com.storemanager.invoicing.usecase.VoidSaleUseCase;
import com.storemanager.sales.domain.Sale;
import com.storemanager.sales.domain.SaleStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end backend tests for the sales -&gt; invoicing flow wired by PR4 (tasks.md task 4.4):
 * every real production use case ({@code RecordSaleUseCase}, {@code IssueVoucherForSaleUseCase},
 * {@code RetryPendingVoucherUseCase}, {@code RetrySaleVoucherUseCase}, {@code CancelSaleUseCase})
 * collaborating exactly as they do in production, against {@code FakeInvoicingProvider} — no
 * Spring context, no real NubeFacT call. {@code RetryPendingVouchersJob}'s own polling loop (the
 * infrastructure-module wrapper that finds every {@code PENDING} voucher and calls {@code
 * RetryPendingVoucherUseCase} per id) is covered separately by {@code RetryPendingVouchersJobTest};
 * here, the same use case is called directly by voucher id to simulate one scheduled poll,
 * matching the job's own behavior exactly.
 */
class SalesInvoicingFlowTest {

    private InMemoryProductRepository productRepository;
    private InMemorySaleRepository saleRepository;
    private InMemoryVoucherRepository voucherRepository;
    private FakeInvoicingProvider invoicingProvider;
    private RecordSaleUseCase recordSaleUseCase;
    private RetryPendingVoucherUseCase retryPendingVoucherUseCase;
    private RetrySaleVoucherUseCase retrySaleVoucherUseCase;
    private CancelSaleUseCase cancelSaleUseCase;
    private Long productId;

    @BeforeEach
    void setUp() {
        productRepository = new InMemoryProductRepository();
        saleRepository = new InMemorySaleRepository();
        voucherRepository = new InMemoryVoucherRepository();
        invoicingProvider = new FakeInvoicingProvider();

        IssueVoucherForSaleUseCase issueVoucherForSaleUseCase =
                new IssueVoucherForSaleUseCase(voucherRepository, invoicingProvider);
        recordSaleUseCase = new RecordSaleUseCase(saleRepository, productRepository, issueVoucherForSaleUseCase);
        retryPendingVoucherUseCase =
                new RetryPendingVoucherUseCase(voucherRepository, saleRepository, invoicingProvider);
        retrySaleVoucherUseCase = new RetrySaleVoucherUseCase(voucherRepository, retryPendingVoucherUseCase);
        VoidSaleUseCase voidSaleUseCase = new VoidSaleUseCase(voucherRepository, invoicingProvider);
        cancelSaleUseCase = new CancelSaleUseCase(voidSaleUseCase, saleRepository);

        productId =
                productRepository
                        .save(
                                Product.createNew(
                                        "iPhone 12", "Usado", 1L, "Apple", new BigDecimal("300"),
                                        new BigDecimal("350"), 10, Instant.now()))
                        .getId();
    }

    private Sale recordASale() {
        return recordSaleUseCase.execute(List.of(new SaleLineItemRequest(productId, 1)), "DNI", "12345678");
    }

    @Test
    void happyPathRecordingASaleIssuesAnAcceptedVoucher() {
        invoicingProvider.alwaysAccept();

        Sale sale = recordASale();

        assertThat(sale.getStatus()).isEqualTo(SaleStatus.COMPLETED);
        Voucher voucher = voucherRepository.findBySaleId(sale.getId()).orElseThrow();
        assertThat(voucher.getStatus()).isEqualTo(VoucherStatus.ACCEPTED);
    }

    @Test
    void forcedFailureLeavesSaleCompletedAndVoucherPendingThenTheScheduledRetryUseCaseSucceeds() {
        invoicingProvider.alwaysReject("Numero de documento invalido");

        Sale sale = recordASale();
        assertThat(sale.getStatus()).isEqualTo(SaleStatus.COMPLETED);
        Voucher pending = voucherRepository.findBySaleId(sale.getId()).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(VoucherStatus.PENDING);

        invoicingProvider.alwaysAccept();
        retryPendingVoucherUseCase.execute(pending.getId());

        Voucher retried = voucherRepository.findBySaleId(sale.getId()).orElseThrow();
        assertThat(retried.getStatus()).isEqualTo(VoucherStatus.ACCEPTED);
    }

    @Test
    void retryExhaustionEscalatesToNeedsAttentionThenManualRetryViaTheSaleScopedUseCaseSucceeds() {
        invoicingProvider.alwaysThrow();
        Sale sale = recordASale();

        Voucher current = voucherRepository.findBySaleId(sale.getId()).orElseThrow();
        int guard = 0;
        while (current.getStatus() == VoucherStatus.PENDING && guard++ < 20) {
            retryPendingVoucherUseCase.execute(current.getId());
            current = voucherRepository.findBySaleId(sale.getId()).orElseThrow();
        }
        assertThat(current.getStatus()).isEqualTo(VoucherStatus.NEEDS_ATTENTION);

        invoicingProvider.alwaysAccept();
        Voucher manuallyRetried = retrySaleVoucherUseCase.execute(sale.getId());

        assertThat(manuallyRetried.getStatus()).isEqualTo(VoucherStatus.ACCEPTED);
    }

    @Test
    void voidFlowViaTheSaleScopedUseCaseVoidsTheVoucherAndCancelsTheSaleWithoutDeletingEither() {
        invoicingProvider.alwaysAccept();
        Sale sale = recordASale();

        Sale cancelled = cancelSaleUseCase.execute(sale.getId(), "Cliente solicito anulacion");

        assertThat(cancelled.getStatus()).isEqualTo(SaleStatus.CANCELLED);
        Voucher voided = voucherRepository.findBySaleId(sale.getId()).orElseThrow();
        assertThat(voided.getStatus()).isEqualTo(VoucherStatus.VOIDED);
        assertThat(saleRepository.findById(sale.getId())).isPresent();
        assertThat(voucherRepository.findBySaleId(sale.getId())).isPresent();
    }
}
